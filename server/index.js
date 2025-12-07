const express = require('express');
const cors = require('cors');
const { sequelize, Company, User, WorkdayEvent, RefuelEvent, LoadingEvent, Settings } = require('./models');
const { Op } = require('sequelize');
const jwt = require('jsonwebtoken');
const authenticateToken = require('./middleware/authenticateToken');
const bcrypt = require('bcryptjs');

const app = express();
const PORT = process.env.PORT || 8080;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static(__dirname));

// HTML Serving
app.get('/', (req, res) => { res.sendFile(__dirname + '/login.html'); });
app.get('/dashboard', authenticateToken, (req, res) => { res.sendFile(__dirname + '/index.html'); });
app.get('/admin', authenticateToken, (req, res) => { res.sendFile(__dirname + '/admin.html'); });
app.get('/settings', authenticateToken, (req, res) => { res.sendFile(__dirname + '/settings.html'); });
app.get('/edit_workday.html', authenticateToken, (req, res) => { res.sendFile(__dirname + '/edit_workday.html'); });
app.get('/edit_refuel.html', authenticateToken, (req, res) => { res.sendFile(__dirname + '/edit_refuel.html'); });

// --- API Routes ---

// Public routes
app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const user = await User.findOne({ where: { username } });
    if (!user || !(await user.isValidPassword(password))) return res.status(401).json({ error: 'Invalid username or password.' });
    
    const token = jwt.sign({ userId: user.id, role: user.role, companyId: user.companyId }, process.env.JWT_SECRET || 'a_very_secret_key_that_should_be_in_env', { expiresIn: '30d' });
    user.lastLoginTime = new Date();
    user.lastLoginLocation = req.ip;
    await user.save();
    res.json({ message: 'Login successful', token, username: user.username, role: user.role });
  } catch (error) {
    res.status(500).json({ error: 'Server error during login.' });
  }
});
app.get('/api/validate-token', authenticateToken, (req, res) => res.json({ valid: true }));

// Settings API
app.get('/api/settings', authenticateToken, async (req, res) => {
    const { userId, companyId } = req.user;
    const targetCompanyId = req.query.companyId || companyId;

    try {
        const userSettings = await Settings.findOne({ where: { userId } });
        const companySettings = await Settings.findOne({ where: { companyId: targetCompanyId } });

        res.json({ 
            userSettings: userSettings?.settings || {},
            companySettings: companySettings?.settings || { currency: 'HUF', visibleFields: {}, customFields: [] } // Default values
        });
    } catch (e) {
        res.status(500).json({ error: 'Failed to get settings.' });
    }
});

app.post('/api/settings', authenticateToken, async (req, res) => {
    const { userId, companyId, role } = req.user;
    const { settings, level, targetCompanyId } = req.body;

    if (!level) return res.status(400).json({ error: 'Level is required.' });

    let whereClause = {};
    if (level === 'user') {
        whereClause = { userId };
    } else if (level === 'company') {
        if (role === 'superadmin' && targetCompanyId) {
            whereClause = { companyId: targetCompanyId };
        } else if (role === 'admin') {
            whereClause = { companyId };
        } else {
            return res.status(403).json({ error: 'Forbidden' });
        }
    } else {
        return res.status(400).json({ error: 'Invalid level.' });
    }

    try {
        const [setting, created] = await Settings.findOrCreate({ where: whereClause, defaults: { settings, ...whereClause } });
        if (!created) {
            const newSettings = { ...setting.settings, ...settings };
            await setting.update({ settings: newSettings });
        }
        res.status(200).json(setting);
    } catch (e) {
        res.status(500).json({ error: 'Failed to save settings.' });
    }
});

// Company routes (for superadmin)
app.get('/api/companies', authenticateToken, async (req, res) => {
    if (req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    try {
        const companies = await Company.findAll({ order: [['name', 'ASC']] });
        res.json(companies);
    } catch (e) {
        res.status(500).json({ error: 'Failed to fetch companies.' });
    }
});

// Workday & Refuel Events API ... (Keep the existing workday/refuel GET/POST endpoints)
app.get('/api/workday-events', authenticateToken, async (req, res) => {
    const { startDate, endDate, carPlate, userId: queryUserId, companyId: queryCompanyId } = req.query;
    const { userId, role, companyId } = req.user;
    const whereClause = {};
    try {
        if (startDate && endDate) whereClause.startTime = { [Op.between]: [new Date(startDate), new Date(endDate)] };
        if (carPlate) whereClause.carPlate = { [Op.iLike]: `%${carPlate}%` };

        if (role === 'user') {
            whereClause.userId = userId;
        } else if (role === 'admin') {
            const usersInCompany = await User.findAll({ where: { companyId, role: {[Op.ne]: 'superadmin'} }, attributes: ['id'] });
            if (queryUserId && queryUserId !== 'all') {
                 const userInCompany = usersInCompany.find(u => u.id === queryUserId);
                 if(userInCompany) whereClause.userId = queryUserId;
                 else return res.status(403).json({error: 'Forbidden'});
            } else {
                 whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
            }
        } else if (role === 'superadmin') {
             if(queryCompanyId && queryCompanyId !== 'all') {
                const usersInCompany = await User.findAll({ where: { companyId: queryCompanyId }, attributes: ['id'] });
                whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
             } else if (queryUserId && queryUserId !== 'all') {
                whereClause.userId = queryUserId
             }
        }

        const events = await WorkdayEvent.findAll({ where: whereClause, include: User, order: [['startTime', 'DESC']] });
        res.json(events);
    } catch (e) { res.status(500).json({error: e.message}) }
});

app.post('/api/workday-events', authenticateToken, async (req, res) => {
    const { userId, role } = req.user;
    let targetUserId = userId;
    if ((role === 'admin' || role === 'superadmin') && req.body.userId) {
        targetUserId = req.body.userId;
    }
    try {
        const event = await WorkdayEvent.create({ ...req.body, userId: targetUserId });
        res.status(201).json(await WorkdayEvent.findByPk(event.id, { include: User }));
    } catch (error) { res.status(500).json({ error: 'Failed to save workday event.' }); }
});

app.get('/api/refuel-events', authenticateToken, async (req, res) => {
    const { startDate, endDate, carPlate, userId: queryUserId, companyId: queryCompanyId } = req.query;
    const { userId, role, companyId } = req.user;
    const whereClause = {};
    try {
        if (startDate && endDate) whereClause.timestamp = { [Op.between]: [new Date(startDate), new Date(endDate)] };
        if (carPlate) whereClause.carPlate = { [Op.iLike]: `%${carPlate}%` };
        
        if (role === 'user') {
            whereClause.userId = userId;
        } else if (role === 'admin') {
            const usersInCompany = await User.findAll({ where: { companyId, role: {[Op.ne]: 'superadmin'} }, attributes: ['id'] });
            if (queryUserId && queryUserId !== 'all') {
                 const userInCompany = usersInCompany.find(u => u.id === queryUserId);
                 if(userInCompany) whereClause.userId = queryUserId;
                 else return res.status(403).json({error: 'Forbidden'});
            } else {
                 whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
            }
        } else if (role === 'superadmin') {
            if(queryCompanyId && queryCompanyId !== 'all') {
                const usersInCompany = await User.findAll({ where: { companyId: queryCompanyId }, attributes: ['id'] });
                whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
             } else if (queryUserId && queryUserId !== 'all') {
                whereClause.userId = queryUserId
             }
        }

        const events = await RefuelEvent.findAll({ where: whereClause, include: User, order: [['timestamp', 'DESC']] });
        res.json(events);
    } catch (e) { res.status(500).json({error: e.message}) }
});

app.post('/api/refuel-events', authenticateToken, async (req, res) => {
    const { userId, role } = req.user;
    let targetUserId = userId;
    if ((role === 'admin' || role === 'superadmin') && req.body.userId) {
        targetUserId = req.body.userId;
    }
    try {
        const event = await RefuelEvent.create({ ...req.body, userId: targetUserId });
        res.status(201).json(await RefuelEvent.findByPk(event.id, { include: User }));
    } catch (error) { res.status(500).json({ error: 'Failed to save refuel event.' }); }
});


// --- Admin & Superadmin Routes ---
const adminRouter = express.Router();
adminRouter.use(authenticateToken, (req, res, next) => {
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    next();
});

adminRouter.get('/users', async (req, res) => {
    const { companyId, role } = req.user;
    try {
        const queryOptions = { attributes: { exclude: ['password'] }, include: { model: Company, attributes: ['name'] }, order: [['username', 'ASC']] };
        if (role === 'admin') {
            queryOptions.where = { companyId: companyId, role: {[Op.ne]: 'superadmin'} };
        }
        res.json(await User.findAll(queryOptions));
    } catch (error) { res.status(500).json({ error: 'Failed to fetch users.' }); }
});

adminRouter.post('/users', async (req, res) => {
    let { username, password, role, realName, companyId, newCompanyName } = req.body;
    const { role: adminRole, companyId: adminCompanyId } = req.user;
    if (!username || !password) return res.status(400).json({ error: 'Username and password are required.' });

    try {
        if (adminRole === 'admin') {
            companyId = adminCompanyId;
            if (role === 'superadmin') return res.status(403).json({ error: 'Admins cannot create superadmins.' });
        }

        if (adminRole === 'superadmin' && newCompanyName) {
            const [company] = await Company.findOrCreate({ where: { name: newCompanyName }, defaults: { name: newCompanyName, adminEmail: `${username}@company.com` } });
            companyId = company.id;
        }

        if (!companyId && role !== 'superadmin') return res.status(400).json({ error: 'Company is required.' });

        const newUser = await User.create({ username, password, realName, role, companyId });
        res.status(201).json(newUser);
    } catch (error) {
        if (error.name === 'SequelizeUniqueConstraintError') return res.status(409).json({ error: 'Username already exists.' });
        res.status(500).json({ error: 'Server error while creating user.' });
    }
});

adminRouter.put('/users/:userId', async (req, res) => {
    const { userId: targetUserId } = req.params;
    let { password, role, realName, companyId, newCompanyName } = req.body;
    const { companyId: adminCompanyId, role: adminRole } = req.user;

    try {
        const userToUpdate = await User.findByPk(targetUserId);
        if (!userToUpdate) return res.status(404).json({ error: 'User not found.' });

        if (userToUpdate.role === 'superadmin' && adminRole !== 'superadmin') return res.status(403).json({ error: 'Permission denied to edit superadmin.' });
        if (adminRole === 'admin' && userToUpdate.companyId !== adminCompanyId) return res.status(403).json({ error: 'Admins can only edit users in their own company.' });
        if (adminRole === 'admin' && role === 'superadmin') return res.status(403).json({ error: 'Admins cannot promote to superadmin.' });

        if (password) userToUpdate.password = await bcrypt.hash(password, 10);
        if (realName) userToUpdate.realName = realName;
        if (role) userToUpdate.role = role;
        
        if (adminRole === 'superadmin' && newCompanyName) {
            const [company] = await Company.findOrCreate({ where: { name: newCompanyName }, defaults: { name: newCompanyName, adminEmail: `${userToUpdate.username}@company.com` } });
            userToUpdate.companyId = company.id;
        } else if (companyId) {
             userToUpdate.companyId = companyId;
        }

        await userToUpdate.save();
        res.json(userToUpdate);
    } catch (error) { res.status(500).json({ error: 'Failed to update user.' }); }
});

app.use('/api/admin', adminRouter);

// --- Server Initialization ---
const createSuperAdmin = async () => {
    try {
        const superadmin = await User.findOne({ where: { username: 'norbapp' } });
        if (!superadmin) {
            await User.create({ username: 'norbapp', password: 'norbapp', role: 'superadmin', realName: 'Super Admin', companyId: null });
        } else {
             if (superadmin.companyId !== null) {
                superadmin.companyId = null;
                await superadmin.save();
             }
        }
    } catch (error) { console.error('Error ensuring superadmin exists:', error); }
};

app.listen(PORT, async () => {
  console.log(`Server listening on port ${PORT}`);
  try {
    await sequelize.authenticate();
    console.log('Database connection established.');
    await sequelize.sync({ alter: true });
    await createSuperAdmin();
    console.log('All models synchronized.');
  } catch (error) { console.error('Unable to connect to the database:', error); }
});
