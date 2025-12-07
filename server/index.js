const express = require('express');
const cors = require('cors');
const db = require('./models');
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
app.get('/dashboard', (req, res) => { res.sendFile(__dirname + '/index.html'); });
app.get('/admin', (req, res) => { res.sendFile(__dirname + '/admin.html'); });
app.get('/settings', (req, res) => { res.sendFile(__dirname + '/settings.html'); });
app.get('/edit_workday.html', (req, res) => { res.sendFile(__dirname + '/edit_workday.html'); });
app.get('/edit_refuel.html', (req, res) => { res.sendFile(__dirname + '/edit_refuel.html'); });

// --- API Routes ---

// Public routes
app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const user = await db.User.findOne({ where: { username } });
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

// Company routes (for superadmin)
app.get('/api/companies', authenticateToken, async (req, res) => {
    if (req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    try {
        const companies = await db.Company.findAll({ order: [['name', 'ASC']] });
        res.json(companies);
    } catch (e) {
        res.status(500).json({ error: 'Failed to fetch companies.' });
    }
});

app.post('/api/companies', authenticateToken, async (req, res) => {
    if (req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    const { name, adminEmail } = req.body;
    try {
        const newCompany = await db.Company.create({ name, adminEmail });
        res.status(201).json(newCompany);
    } catch (error) {
        res.status(500).json({ error: 'Failed to create company.' });
    }
});

app.put('/api/companies/:id', authenticateToken, async (req, res) => {
    if (req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    const { name, adminEmail } = req.body;
    try {
        const company = await db.Company.findByPk(req.params.id);
        if (!company) return res.status(404).json({ error: 'Company not found' });
        company.name = name || company.name;
        company.adminEmail = adminEmail || company.adminEmail;
        await company.save();
        res.json(company);
    } catch (error) {
        res.status(500).json({ error: 'Failed to update company.' });
    }
});

app.delete('/api/companies/:id', authenticateToken, async (req, res) => {
    if (req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    try {
        const company = await db.Company.findByPk(req.params.id);
        if (!company) return res.status(404).json({ error: 'Company not found' });
        await company.destroy();
        res.status(204).send();
    } catch (error) {
        res.status(500).json({ error: 'Failed to delete company.' });
    }
});

// Workday Events API
app.get('/api/workday-events/:id', authenticateToken, async (req, res) => {
    const { id } = req.params;
    const { userId, role } = req.user;
    try {
        const event = await db.WorkdayEvent.findByPk(id);
        if (!event) return res.status(404).json({ error: 'Workday event not found' });
        if (role !== 'superadmin' && event.userId !== userId) return res.status(403).json({ error: 'Forbidden' });
        res.json(event);
    } catch (error) { res.status(500).json({ error: 'Failed to fetch workday event.' }); }
});

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
            const usersInCompany = await db.User.findAll({ where: { companyId, role: {[Op.ne]: 'superadmin'} }, attributes: ['id'] });
            if (queryUserId && queryUserId !== 'all') {
                 const userInCompany = usersInCompany.find(u => u.id === queryUserId);
                 if(userInCompany) whereClause.userId = queryUserId;
                 else return res.status(403).json({error: 'Forbidden'});
            } else {
                 whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
            }
        } else if (role === 'superadmin') {
             if(queryCompanyId && queryCompanyId !== 'all') {
                const usersInCompany = await db.User.findAll({ where: { companyId: queryCompanyId }, attributes: ['id'] });
                whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
             } else if (queryUserId && queryUserId !== 'all') {
                whereClause.userId = queryUserId
             }
        }

        const events = await db.WorkdayEvent.findAll({ where: whereClause, include: db.User, order: [['startTime', 'DESC']] });
        res.json(events);
    } catch (e) { res.status(500).json({error: e.message}) }
});

app.post('/api/workday-events', authenticateToken, async (req, res) => {
    const { userId, role } = req.user;
    const { userId: targetUserIdBody, ...workdayData } = req.body;

    let targetUserId = userId;
    if ((role === 'admin' || role === 'superadmin') && targetUserIdBody) {
        targetUserId = targetUserIdBody;
    }

    try {
        const event = await db.WorkdayEvent.create({ ...workdayData, userId: targetUserId });
        res.status(201).json(await db.WorkdayEvent.findByPk(event.id, { include: db.User }));
    } catch (error) { 
        console.error('Error saving workday event:', error);
        res.status(500).json({ error: 'Failed to save workday event.' }); 
    }
});

app.put('/api/workday-events/:id', authenticateToken, async (req, res) => {
    const { id } = req.params;
    const { userId, role } = req.user;
    try {
        const event = await db.WorkdayEvent.findByPk(id);
        if (!event) return res.status(404).json({ error: 'Workday event not found' });
        if (role !== 'superadmin' && event.userId !== userId) return res.status(403).json({ error: 'Forbidden' });
        
        await event.update(req.body);
        res.json(await db.WorkdayEvent.findByPk(id, { include: db.User }));
    } catch (error) { res.status(500).json({ error: 'Failed to update workday event.' }); }
});

app.delete('/api/workday-events/:id', authenticateToken, async (req, res) => {
    const { id } = req.params;
    const { userId, role } = req.user;
    try {
        const event = await db.WorkdayEvent.findByPk(id);
        if (!event) return res.status(404).json({ error: 'Workday event not found' });
        if (role !== 'superadmin' && event.userId !== userId) return res.status(403).json({ error: 'Forbidden' });
        
        await event.destroy();
        res.status(204).send();
    } catch (error) { res.status(500).json({ error: 'Failed to delete workday event.' }); }
});

// Refuel Events API
app.get('/api/refuel-events/:id', authenticateToken, async (req, res) => {
    const { id } = req.params;
    const { userId, role } = req.user;
    try {
        const event = await db.RefuelEvent.findByPk(id);
        if (!event) return res.status(404).json({ error: 'Refuel event not found' });
        if (role !== 'superadmin' && event.userId !== userId) return res.status(403).json({ error: 'Forbidden' });
        res.json(event);
    } catch (error) { res.status(500).json({ error: 'Failed to fetch refuel event.' }); }
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
            const usersInCompany = await db.User.findAll({ where: { companyId, role: {[Op.ne]: 'superadmin'} }, attributes: ['id'] });
            if (queryUserId && queryUserId !== 'all') {
                 const userInCompany = usersInCompany.find(u => u.id === queryUserId);
                 if(userInCompany) whereClause.userId = queryUserId;
                 else return res.status(403).json({error: 'Forbidden'});
            } else {
                 whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
            }
        } else if (role === 'superadmin') {
             if(queryCompanyId && queryCompanyId !== 'all') {
                const usersInCompany = await db.User.findAll({ where: { companyId: queryCompanyId }, attributes: ['id'] });
                whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
             } else if (queryUserId && queryUserId !== 'all') {
                whereClause.userId = queryUserId
             }
        }

        const events = await db.RefuelEvent.findAll({ where: whereClause, include: db.User, order: [['timestamp', 'DESC']] });
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
        const event = await db.RefuelEvent.create({ ...req.body, userId: targetUserId });
        res.status(201).json(await db.RefuelEvent.findByPk(event.id, { include: db.User }));
    } catch (error) { res.status(500).json({ error: 'Failed to save refuel event.' }); }
});

app.put('/api/refuel-events/:id', authenticateToken, async (req, res) => {
    const { id } = req.params;
    const { userId, role } = req.user;
    try {
        const event = await db.RefuelEvent.findByPk(id);
        if (!event) return res.status(404).json({ error: 'Refuel event not found' });
        if (role !== 'superadmin' && event.userId !== userId) return res.status(403).json({ error: 'Forbidden' });

        await event.update(req.body);
        res.json(await db.RefuelEvent.findByPk(id, { include: db.User }));
    } catch (error) { res.status(500).json({ error: 'Failed to update refuel event.' }); }
});

app.delete('/api/refuel-events/:id', authenticateToken, async (req, res) => {
    const { id } = req.params;
    const { userId, role } = req.user;
    try {
        const event = await db.RefuelEvent.findByPk(id);
        if (!event) return res.status(404).json({ error: 'Refuel event not found' });
        if (role !== 'superadmin' && event.userId !== userId) return res.status(403).json({ error: 'Forbidden' });

        await event.destroy();
        res.status(204).send();
    } catch (error) { res.status(500).json({ error: 'Failed to delete refuel event.' }); }
});

// --- Admin Settings ---
app.get('/api/settings', authenticateToken, async (req, res) => {
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') {
        return res.status(403).json({ error: 'Forbidden' });
    }
    try {
        const settings = await db.Settings.findOne();
        res.json(settings ? settings.settings : {});
    } catch (error) {
        res.status(500).json({ error: 'Failed to load settings.' });
    }
});

app.post('/api/settings', authenticateToken, async (req, res) => {
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') {
        return res.status(403).json({ error: 'Forbidden' });
    }
    try {
        const newSettings = req.body;
        let settings = await db.Settings.findOne();
        if (settings) {
            settings.settings = { ...settings.settings, ...newSettings };
            await settings.save();
        } else {
            settings = await db.Settings.create({ settings: newSettings });
        }
        res.status(200).json({ message: 'Settings saved', settings: settings.settings });
    } catch (error) {
        res.status(500).json({ error: 'Failed to save settings.' });
    }
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
        const queryOptions = { attributes: { exclude: ['password'] }, include: { model: db.Company, attributes: ['name'] }, order: [['username', 'ASC']] };
        if (role === 'admin') {
            queryOptions.where = { companyId: companyId, role: {[Op.ne]: 'superadmin'} };
        }
        res.json(await db.User.findAll(queryOptions));
    } catch (error) { res.status(500).json({ error: 'Failed to fetch users.' }); }
});

adminRouter.post('/users', async (req, res) => {
    const { username, realName, password, role, companyId: reqCompanyId } = req.body;
    const { companyId: adminCompanyId, role: adminRole } = req.user;
    try {
        let targetCompanyId = adminCompanyId;
        if (adminRole === 'superadmin' && reqCompanyId) {
            targetCompanyId = reqCompanyId;
        }
        const newUser = await db.User.create({ username, realName, password, role, companyId: targetCompanyId });
        res.status(201).json(newUser);
    } catch (error) {
        res.status(500).json({ error: 'Failed to create user.' });
    }
});

adminRouter.put('/users/:id', async (req, res) => {
    const { id } = req.params;
    const { realName, password, role, companyId: reqCompanyId } = req.body;
    const { companyId: adminCompanyId, role: adminRole } = req.user;

    try {
        const user = await db.User.findByPk(id);
        if (!user) return res.status(404).json({ error: 'User not found' });

        if (adminRole === 'admin' && user.companyId !== adminCompanyId) {
            return res.status(403).json({ error: 'Forbidden' });
        }

        user.realName = realName || user.realName;
        user.role = role || user.role;
        if (password) {
            const salt = await bcrypt.genSalt(10);
            user.password = await bcrypt.hash(password, salt);
        }
        if (adminRole === 'superadmin' && reqCompanyId) {
            user.companyId = reqCompanyId;
        }

        await user.save();
        res.json(user);
    } catch (error) {
        res.status(500).json({ error: 'Failed to update user.' });
    }
});

adminRouter.delete('/users/:id', async (req, res) => {
    const { id } = req.params;
    const { companyId: adminCompanyId, role: adminRole } = req.user;

    try {
        const user = await db.User.findByPk(id);
        if (!user) return res.status(404).json({ error: 'User not found' });

        if (adminRole === 'admin' && user.companyId !== adminCompanyId) {
            return res.status(403).json({ error: 'Forbidden' });
        }

        await user.destroy();
        res.status(204).send();
    } catch (error) { res.status(500).json({ error: 'Failed to delete user.' }); }
});

app.use('/api/admin', adminRouter);

// --- Server Initialization ---
const createSuperAdmin = async () => {
    try {
        const superadmin = await db.User.findOne({ where: { username: 'norbapp' } });
        if (!superadmin) {
            await db.User.create({ username: 'norbapp', password: 'norbapp', role: 'superadmin', realName: 'Super Admin', companyId: null });
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
  console.log(`NODE_ENV is: ${process.env.NODE_ENV}`);
  if (process.env.DB_CONNECTION_STRING) {
      console.log('DB_CONNECTION_STRING is set.');
  } else {
      console.log('DB_CONNECTION_STRING is NOT set, using default localhost.');
  }
  try {
    await db.sequelize.authenticate();
    console.log('Database connection established successfully.');
    await db.sequelize.sync({ alter: true });
    console.log('All models were synchronized successfully.');
    await createSuperAdmin();
    console.log('Superadmin check/creation complete.');
  } catch (error) {
    console.error('Unable to connect to the database or sync models:', error);
  }
});
