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


// --- Admin Routes ---
const adminRouter = express.Router();
adminRouter.use(authenticateToken, (req, res, next) => {
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') {
        return res.status(403).json({ error: 'Forbidden: Admins or Superadmin only.' });
    }
    next();
});

adminRouter.get('/users', async (req, res) => {
    const { companyId, role } = req.user;
    try {
        const queryOptions = {
            attributes: { exclude: ['password'] },
            include: { model: Company, attributes: ['name'] },
            order: [['username', 'ASC']]
        };

        if (role === 'admin') {
            queryOptions.where = {
                companyId: companyId,
                role: {[Op.ne]: 'superadmin'} // Admins cannot see superadmins
            };
        }

        res.json(await User.findAll(queryOptions));
    } catch (error) {
        console.error("User fetch error:", error);
        res.status(500).json({ error: 'Failed to fetch users.' });
    }
});

adminRouter.post('/users', async (req, res) => {
    const { username, password, role, companyName, realName } = req.body;
    const { role: adminRole, companyId: adminCompanyId } = req.user;

    if (!username || !password) return res.status(400).json({ error: 'Username and password are required.' });

    let finalCompanyId = adminRole === 'admin' ? adminCompanyId : null;
    let finalRole = role || 'user';

    if (adminRole === 'admin' && finalRole === 'superadmin') {
         return res.status(403).json({ error: 'Admins cannot create superadmins.' });
    }

    if (adminRole === 'superadmin' && companyName) {
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

    if (!finalCompanyId && finalRole !== 'superadmin') {
        return res.status(400).json({ error: 'Company assignment is required.' });
    }

    try {
        const newUser = await User.create({ 
            username, 
            password, 
            realName,
            role: finalRole, 
            companyId: finalCompanyId, 
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
    const { password, role, realName, companyName } = req.body;
    const { companyId: adminCompanyId, role: adminRole } = req.user;

    try {
        const userToUpdate = await User.findByPk(targetUserId);
        if (!userToUpdate) return res.status(404).json({ error: 'User not found.' });

        // Security check: No one can edit a superadmin except another superadmin (or themselves)
        if (userToUpdate.role === 'superadmin' && adminRole !== 'superadmin') {
            return res.status(403).json({ error: 'You do not have permission to edit a superadmin.'});
        }

        // Admin specific rules
        if (adminRole === 'admin') {
            if (userToUpdate.companyId !== adminCompanyId) {
                return res.status(403).json({ error: 'Forbidden: You can only edit users in your own company.' });
            }
            if (role === 'superadmin') { // Admins cannot promote to superadmin
                return res.status(403).json({ error: 'Admins cannot assign superadmin role.' });
            }
        }

        if (password) userToUpdate.password = await bcrypt.hash(password, 10);
        if (realName) userToUpdate.realName = realName;
        if (role) userToUpdate.role = role;
        
        // Superadmin can change company by name
        if (adminRole === 'superadmin' && companyName) {
            const [company] = await Company.findOrCreate({
                where: { name: companyName },
                defaults: { name: companyName, adminEmail: `${userToUpdate.username}@company.com` }
            });
            userToUpdate.companyId = company.id;
        }
        
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
