const express = require('express');
const cors = require('cors');
const { sequelize, Company, User, WorkdayEvent, RefuelEvent, LoadingEvent, Op } = require('./models');
const jwt = require('jsonwebtoken');
const authenticateToken = require('./middleware/authenticateToken');
const bcrypt = require('bcryptjs');

const app = express();
const PORT = process.env.PORT || 8080;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static(__dirname)); // Serve static files like login.html

// --- HTML Serving ---
app.get('/', (req, res) => {
  res.sendFile(__dirname + '/login.html');
});

app.get('/dashboard', (req, res) => {
    res.sendFile(__dirname + '/index.html');
});

app.get('/admin', (req, res) => {
    res.sendFile(__dirname + '/admin.html');
});

app.get('/edit_workday.html', (req, res) => {
  res.sendFile(__dirname + '/edit_workday.html');
});

app.get('/edit_refuel.html', (req, res) => {
  res.sendFile(__dirname + '/edit_refuel.html');
});

// --- Public API Routes ---

app.post('/api/register', async (req, res) => {
    const { username, password, role, companyName, adminEmail } = req.body;

    if (!username || !password || !role) {
        return res.status(400).json({ error: 'Username, password, and role are required.' });
    }
    if (role === 'admin' && (!companyName || !adminEmail)) {
        return res.status(400).json({ error: 'Company name and admin email are required for admin registration.' });
    }

    try {
        const existingUser = await User.findOne({ where: { username } });
        if (existingUser) {
            return res.status(409).json({ error: 'Username already taken.' });
        }

        const [company] = await Company.findOrCreate({
            where: { name: companyName },
            defaults: { adminEmail: adminEmail }
        });

        const newUser = await User.create({
            username,
            password,
            role,
            companyId: company.id,
            realName: req.body.realName || username,
            permissions: role === 'user' ? ['CAN_VIEW_OWN_DATA'] : (role === 'admin' ? ['CAN_MANAGE_USERS'] : [])
        });

        res.status(201).json({
            message: 'User registered successfully.',
            userId: newUser.id,
            username: newUser.username,
            role: newUser.role
        });

    } catch (error) {
        console.error('Registration error:', error);
        res.status(500).json({ error: 'Server error during registration.' });
    }
});

app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const user = await User.findOne({ where: { username } });
    if (!user || !(await user.isValidPassword(password))) {
      return res.status(401).json({ error: 'Invalid username or password.' });
    }
    const token = jwt.sign(
      { userId: user.id, role: user.role, companyId: user.companyId },
      process.env.JWT_SECRET || 'a_very_secret_key_that_should_be_in_env',
      { expiresIn: '30d' }
    );
    user.lastLoginTime = new Date();
    user.lastLoginLocation = req.ip;
    await user.save();
    res.json({
        message: 'Login successful',
        token,
        username: user.username,
        role: user.role,
        permissions: user.permissions || []
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Server error during login.' });
  }
});

app.post('/api/logout', (req, res) => {
    res.json({ message: 'Logged out successfully.' });
});

app.get('/api/validate-token', authenticateToken, (req, res) => {
    res.json({ valid: true });
});

// --- Data API Routes (Protected) ---

const canAccessResource = (model) => async (req, res, next) => {
    const { id } = req.params;
    const { userId, role, companyId } = req.user;

    try {
        const resource = await model.findByPk(id);
        if (!resource) {
            return res.status(404).json({ error: 'Resource not found' });
        }
        if (role === 'superadmin') return next();
        if (role === 'user' && resource.userId === userId) return next();
        if (role === 'admin') {
            const resourceOwner = await User.findByPk(resource.userId);
            if (resourceOwner && resourceOwner.companyId === companyId) return next();
        }
        return res.status(403).json({ error: 'Forbidden: You do not have access to this resource.' });
    } catch (error) {
        console.error('Authorization error:', error);
        return res.status(500).json({ error: 'Server error during authorization.' });
    }
};

// Workday Events
app.get('/api/workday-events', authenticateToken, async (req, res) => {
    try {
        const { startDate, endDate, carPlate } = req.query;
        const { userId, role, companyId } = req.user;
        const whereClause = {};

        if (startDate && endDate) { whereClause.startTime = { [Op.between]: [new Date(startDate), new Date(endDate)] }; }
        if (carPlate) { whereClause.carPlate = { [Op.iLike]: `%${carPlate}%` }; }

        if (role === 'user') {
            whereClause.userId = userId;
        } else if (role === 'admin') {
            const usersInCompany = await User.findAll({ where: { companyId }, attributes: ['id'] });
            whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
        } 
        const events = await WorkdayEvent.findAll({ where: whereClause, include: User, order: [['startTime', 'DESC']] });
        res.json(events);
    } catch (error) {
        res.status(500).json({ error: 'Failed to fetch workday events.' });
    }
});

// --- Admin Routes ---
const adminRouter = express.Router();
adminRouter.use(authenticateToken, isAdminOrSuperAdmin);

function isAdminOrSuperAdmin(req, res, next) {
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') {
        return res.status(403).json({ error: 'Forbidden: Admins or Superadmin only.' });
    }
    next();
};

adminRouter.get('/users', async (req, res) => {
    const { userId, companyId, role } = req.user;
    try {
        const queryOptions = {
            attributes: { exclude: ['password'] },
            include: { model: Company, attributes: ['name'] }
        };

        if (role === 'admin') {
            queryOptions.where = {
                companyId: companyId,
                [Op.or]: [
                    { role: 'user' },
                    { id: userId } // Include the admin themselves
                ]
            };
        } else { // superadmin
            // No filter, superadmin sees all
        }

        res.json(await User.findAll(queryOptions));
    } catch (error) {
        console.error("User fetch error:", error);
        res.status(500).json({ error: 'Failed to fetch users.' });
    }
});

adminRouter.post('/users', async (req, res) => {
    const { username, password, role, permissions, companyId, companyName, realName } = req.body;
    const { role: adminRole } = req.user;

    if (!username || !password) return res.status(400).json({ error: 'Username and password are required.' });

    let finalCompanyId = companyId;

    if (adminRole === 'admin') {
        if (role && role !== 'user') {
             return res.status(403).json({ error: 'Admins can only create users.' });
        }
        finalCompanyId = req.user.companyId;
    }

    if (adminRole === 'superadmin' && role === 'admin' && companyName) {
        try {
            const [company] = await Company.findOrCreate({
                where: { name: companyName }, 
                defaults: { name: companyName, adminEmail: `${username}@company.com` } // Placeholder email
            });
            finalCompanyId = company.id;
        } catch(e) {
             return res.status(500).json({ error: 'Could not create or find company.' });
        }
    }

    if (!finalCompanyId && role !== 'superadmin') {
        return res.status(400).json({ error: 'Company assignment is required.' });
    }

    try {
        const newUser = await User.create({ 
            username, 
            password, 
            realName,
            role: role || 'user', 
            companyId: finalCompanyId, 
            permissions 
        });
        const { password: _, ...userResponse } = newUser.get({ plain: true });
        res.status(201).json({ message: 'User created successfully', user: userResponse });
    } catch (error) {
        if (error.name === 'SequelizeUniqueConstraintError') return res.status(409).json({ error: 'Username already exists.' });
        console.error("User creation error:", error);
        res.status(500).json({ error: 'Server error while creating user.' });
    }
});

adminRouter.put('/users/:userId', async (req, res) => {
    const { userId: targetUserId } = req.params;
    const { password, role, permissions, realName } = req.body;
    const { companyId: adminCompanyId, role: adminRole } = req.user;

    try {
        const userToUpdate = await User.findByPk(targetUserId);
        if (!userToUpdate) return res.status(404).json({ error: 'User not found.' });

        if (adminRole === 'admin') {
            if (userToUpdate.companyId !== adminCompanyId) {
                return res.status(403).json({ error: 'Forbidden: You can only edit users in your own company.' });
            }
            if (role && userToUpdate.id !== req.user.userId) { // Admins can't change roles of others
                return res.status(403).json({ error: 'Admins cannot change roles.' });
            }
        }

        if (password) {
            userToUpdate.password = await bcrypt.hash(password, await bcrypt.genSalt(10));
        } 
        if (realName) userToUpdate.realName = realName;
        if (role && adminRole === 'superadmin') userToUpdate.role = role;
        if (permissions) userToUpdate.permissions = permissions;

        await userToUpdate.save();
        const { password: _, ...userResponse } = userToUpdate.get({ plain: true });
        res.json({ message: 'User updated successfully.', user: userResponse });
    } catch (error) {
        console.error("User update error:", error);
        res.status(500).json({ error: 'Failed to update user.' });
    }
});


app.use('/api/admin', adminRouter);

// --- Server Initialization ---
const createSuperAdmin = async () => {
    try {
        const superadmin = await User.findOne({ where: { username: 'norbapp' } });
        if (!superadmin) {
            await User.create({ username: 'norbapp', password: 'norbapp', role: 'superadmin', realName: 'Super Admin', permissions: ['ALL'] });
            console.log('Superadmin user "norbapp" created.');
        } else {
             if (!(await superadmin.isValidPassword('norbapp'))) {
                 superadmin.password = await bcrypt.hash('norbapp', await bcrypt.genSalt(10));
                 await superadmin.save();
                 console.log('Superadmin password has been reset.');
             }
        }
    } catch (error) {
        console.error('Error ensuring superadmin exists:', error);
    }
};

app.listen(PORT, async () => {
  console.log(`Server listening on port ${PORT}`);
  try {
    await sequelize.authenticate();
    console.log('Database connection established.');
    await sequelize.sync({ alter: true });
    console.log('All models synchronized.');
    await createSuperAdmin();
  } catch (error) {
    console.error('Unable to connect to the database:', error);
  }
});
