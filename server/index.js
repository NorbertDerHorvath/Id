console.log('--- SERVER STARTING: DEPLOYMENT-CHECK-V2 ---');
const express = require('express');
const cors = require('cors');
const db = require('./models');
const { Op } = db.Sequelize;
const jwt = require('jsonwebtoken');
const authenticateToken = require('./middleware/authenticateToken');
const bcrypt = require('bcryptjs');

const app = express();
const PORT = process.env.PORT || 8080;

app.use(cors());
app.use(express.json());
app.use(express.static(__dirname));

console.log('Middleware configured.');

app.get('/', (req, res) => { res.sendFile(__dirname + '/login.html'); });
app.get('/dashboard', (req, res) => { res.sendFile(__dirname + '/index.html'); });
app.get('/admin', (req, res) => { res.sendFile(__dirname + '/admin.html'); });
app.get('/settings', (req, res) => { res.sendFile(__dirname + '/settings.html'); });
app.get('/edit_workday.html', (req, res) => { res.sendFile(__dirname + '/edit_workday.html'); });
app.get('/edit_refuel.html', (req, res) => { res.sendFile(__dirname + '/edit_refuel.html'); });

console.log('HTML routes configured.');

app.post('/api/login', async (req, res) => {
  console.log('POST /api/login received');
  const { username, password } = req.body;
  try {
    const user = await db.User.findOne({ where: { username } });
    if (!user || !(await user.isValidPassword(password))) {
      console.log(`Login failed for user: ${username}`);
      return res.status(401).json({ error: 'Invalid username or password.' });
    }
    
    const token = jwt.sign({ userId: user.id, role: user.role, companyId: user.companyId }, process.env.JWT_SECRET || 'a_very_secret_key_that_should_be_in_env', { expiresIn: '30d' });
    user.lastLoginTime = new Date();
    user.lastLoginLocation = req.ip;
    await user.save();
    console.log(`Login successful for user: ${username}`);
    res.json({ message: 'Login successful', token, username: user.username, role: user.role });
  } catch (error) {
    console.error('Error during login:', error);
    res.status(500).json({ error: 'Server error during login.' });
  }
});

app.get('/api/settings', authenticateToken, async (req, res) => {
    console.log(`GET /api/settings request received for user: ${req.user.userId}`);
    const { role, companyId } = req.user;
    let targetCompanyId = companyId;

    if (role === 'superadmin') {
        if (req.query.companyId) {
            targetCompanyId = req.query.companyId;
        } else {
            // Superadmin must specify a company
            return res.status(400).json({ error: 'Company ID is required for superadmin.' });
        }
    } else if (role !== 'admin') {
        return res.status(403).json({ error: 'Forbidden' });
    }

    try {
        const settings = await db.Settings.findOne({ where: { companyId: targetCompanyId } });
        let currentSettings = settings ? settings.settings : {};
        if (currentSettings.maintenance_mode !== undefined) {
            currentSettings.maintenance_mode = String(currentSettings.maintenance_mode).toLowerCase() === 'true';
        } else {
            currentSettings.maintenance_mode = false; 
        }
        console.log('Settings fetched from DB for company:', targetCompanyId, currentSettings);
        res.json(currentSettings);
    } catch (error) {
        console.error('Error loading settings:', error);
        res.status(500).json({ error: 'Failed to load settings.' });
    }
});

app.post('/api/settings', authenticateToken, async (req, res) => {
    console.log(`POST /api/settings request received for user: ${req.user.userId}`);
    const { role, companyId } = req.user;
    const { newSettings, targetCompanyId: reqCompanyId } = req.body;

    let targetCompanyId = companyId;
    if (role === 'superadmin') {
        if (!reqCompanyId) {
            return res.status(400).json({ error: 'Company ID is required for superadmin.' });
        }
        targetCompanyId = reqCompanyId;
    } else if (role !== 'admin') {
        return res.status(403).json({ error: 'Forbidden' });
    }

    try {
        if (newSettings.maintenance_mode !== undefined) {
            newSettings.maintenance_mode = newSettings.maintenance_mode === 'true' || newSettings.maintenance_mode === true;
        }

        let settings = await db.Settings.findOne({ where: { companyId: targetCompanyId } });
        if (settings) {
            settings.settings = { ...settings.settings, ...newSettings };
            settings.changed('settings', true);
            await settings.save();
        } else {
            settings = await db.Settings.create({ companyId: targetCompanyId, settings: newSettings });
        }
        console.log('Settings saved to DB for company:', targetCompanyId, settings.settings);
        res.status(200).json({ message: 'Settings saved', settings: settings.settings });
    } catch (error) {
        console.error('Error saving settings:', error);
        res.status(500).json({ error: 'Failed to save settings.' });
    }
});

const adminRouter = express.Router();
adminRouter.use(authenticateToken, (req, res, next) => {
    console.log(`Admin route requested by user: ${req.user.userId}`);
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') {
        console.log(`Forbidden admin route access for user: ${req.user.userId}`);
        return res.status(403).json({ error: 'Forbidden' });
    }
    next();
});

adminRouter.get('/users', async (req, res) => {
    console.log('GET /api/admin/users request received');
    const { companyId, role } = req.user;
    try {
        const queryOptions = { attributes: { exclude: ['password'] }, include: [{ model: db.Company, attributes: ['name'] }], order: [['username', 'ASC']] };
        if (role === 'admin') {
            queryOptions.where = { companyId: companyId, role: {[Op.ne]: 'superadmin'} };
        }
        const users = await db.User.findAll(queryOptions);
        console.log(`Found ${users.length} users`);
        res.json(users);
    } catch (error) {
        console.error('Error fetching users:', error);
        res.status(500).json({ error: 'Failed to fetch users.' });
    }
});

adminRouter.get('/companies', async (req, res) => {
    console.log('GET /api/admin/companies request received');
    if (req.user.role !== 'superadmin') {
        return res.status(403).json({ error: 'Forbidden' });
    }
    try {
        const companies = await db.Company.findAll({ order: [['name', 'ASC']] });
        res.json(companies);
    } catch (error) {
        console.error('Error fetching companies:', error);
        res.status(500).json({ error: 'Failed to fetch companies.' });
    }
});

app.use('/api/admin', adminRouter);

// Workday and Refuel events
const eventsRouter = express.Router();
eventsRouter.use(authenticateToken);

eventsRouter.get('/workday-events', async (req, res) => {
    const { startDate, endDate, userId, companyId } = req.query;
    const where = {};
    if (startDate) where.startTime = { [Op.gte]: new Date(startDate) };
    if (endDate) where.endTime = { [Op.lte]: new Date(endDate) };
    if (userId) where.userId = userId;
    if (companyId) {
        const users = await db.User.findAll({ where: { companyId }, attributes: ['id'] });
        where.userId = { [Op.in]: users.map(u => u.id) };
    }
    const events = await db.WorkdayEvent.findAll({ where, include: [db.User] });
    res.json(events);
});

eventsRouter.post('/workday-events', async (req, res) => {
    const newEvent = await db.WorkdayEvent.create(req.body);
    res.status(201).json(newEvent);
});

eventsRouter.delete('/workday-events/:id', async (req, res) => {
    await db.WorkdayEvent.destroy({ where: { id: req.params.id } });
    res.status(204).send();
});

eventsRouter.get('/refuel-events', async (req, res) => {
    const { startDate, endDate, userId, companyId } = req.query;
    const where = {};
    if (startDate) where.timestamp = { [Op.gte]: new Date(startDate) };
    if (endDate) where.timestamp = { [Op.lte]: new Date(endDate) };
    if (userId) where.userId = userId;
    if (companyId) {
        const users = await db.User.findAll({ where: { companyId }, attributes: ['id'] });
        where.userId = { [Op.in]: users.map(u => u.id) };
    }
    const events = await db.RefuelEvent.findAll({ where, include: [db.User] });
    res.json(events);
});

eventsRouter.post('/refuel-events', async (req, res) => {
    const newEvent = await db.RefuelEvent.create(req.body);
    res.status(201).json(newEvent);
});

eventsRouter.delete('/refuel-events/:id', async (req, res) => {
    await db.RefuelEvent.destroy({ where: { id: req.params.id } });
    res.status(204).send();
});

app.use('/api', eventsRouter);

console.log('API routes configured.');

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
  } catch (error) {
    console.error('Unable to connect to the database or sync models:', error);
  }
});