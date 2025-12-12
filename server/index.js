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

// --- SETTINGS DEFAULTS ---
const defaultSettings = {
    maintenance_mode: false,
    currency: 'Ft',
    fields: {
        workday_startTime: true,
        workday_endTime: true,
        workday_startLocation: true,
        workday_endLocation: true,
        workday_carPlate: true,
        workday_startOdometer: true,
        workday_endOdometer: true,
        workday_breakTime: true,
        refuel_odometer: true,
        refuel_fuelType: true,
        refuel_price: true,
        refuel_paymentMethod: true,
        refuel_location: true,
    }
};

const mergeSettings = (savedSettings) => {
    const finalSettings = JSON.parse(JSON.stringify(defaultSettings));
    if (!savedSettings) return finalSettings;

    finalSettings.maintenance_mode = savedSettings.maintenance_mode !== undefined ? savedSettings.maintenance_mode : finalSettings.maintenance_mode;
    finalSettings.currency = savedSettings.currency || finalSettings.currency;
    if (savedSettings.fields) {
        for (const key in finalSettings.fields) {
            if (savedSettings.fields[key] !== undefined) {
                finalSettings.fields[key] = savedSettings.fields[key];
            }
        }
    }
    return finalSettings;
};

app.get('/', (req, res) => { res.sendFile(__dirname + '/login.html'); });
app.get('/dashboard', (req, res) => { res.sendFile(__dirname + '/index.html'); });
app.get('/admin', (req, res) => { res.sendFile(__dirname + '/admin.html'); });
app.get('/settings', (req, res) => { res.sendFile(__dirname + '/settings.html'); });
app.get('/edit_workday.html', (req, res) => { res.sendFile(__dirname + '/edit_workday.html'); });
app.get('/edit_refuel.html', (req, res) => { res.sendFile(__dirname + '/edit_refuel.html'); });

console.log('HTML routes configured.');

app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const user = await db.User.findOne({ where: { username } });
    if (!user || !(await user.isValidPassword(password))) {
      return res.status(401).json({ error: 'Invalid username or password.' });
    }
    const token = jwt.sign({ userId: user.id, role: user.role, companyId: user.companyId }, process.env.JWT_SECRET || 'a_very_secret_key_that_should_be_in_env', { expiresIn: '30d' });
    user.lastLoginTime = new Date();
    user.lastLoginLocation = req.ip;
    await user.save();
    res.json({ message: 'Login successful', token, username: user.username, role: user.role });
  } catch (error) {
    console.error('Error during login:', error);
    res.status(500).json({ error: 'Server error during login.' });
  }
});

app.get('/api/my-settings', authenticateToken, async (req, res) => {
    try {
        const settings = await db.Settings.findOne({ where: { companyId: req.user.companyId } });
        res.json(mergeSettings(settings ? settings.settings : {}));
    } catch (error) {
        res.status(500).json({ error: 'Failed to load company settings.' });
    }
});

app.get('/api/settings', authenticateToken, async (req, res) => {
    const { role, companyId } = req.user;
    let targetCompanyId = companyId;
    if (role === 'superadmin') {
        if (req.query.companyId) targetCompanyId = req.query.companyId;
        else return res.json(defaultSettings);
    } else if (role !== 'admin') {
        return res.status(403).json({ error: 'Forbidden' });
    }
    try {
        const settings = await db.Settings.findOne({ where: { companyId: targetCompanyId } });
        res.json(mergeSettings(settings ? settings.settings : {}));
    } catch (error) {
        res.status(500).json({ error: 'Failed to load settings.' });
    }
});

app.post('/api/settings', authenticateToken, async (req, res) => {
    const { role, companyId } = req.user;
    const { newSettings, targetCompanyId: reqCompanyId } = req.body;
    let targetCompanyId = companyId;
    if (role === 'superadmin') {
        if (!reqCompanyId) return res.status(400).json({ error: 'Company ID is required.' });
        targetCompanyId = reqCompanyId;
    } else if (role !== 'admin') {
        return res.status(403).json({ error: 'Forbidden' });
    }
    try {
        if (newSettings.fields) {
            for (const key in newSettings.fields) {
                 newSettings.fields[key] = newSettings.fields[key] === true;
            }
        }
        let settings = await db.Settings.findOne({ where: { companyId: targetCompanyId } });
        if (settings) {
            const merged = { ...settings.settings, ...newSettings, fields: { ...settings.settings.fields, ...newSettings.fields } };
            settings.settings = merged;
            settings.changed('settings', true);
            await settings.save();
        } else {
            settings = await db.Settings.create({ companyId: targetCompanyId, settings: newSettings });
        }
        res.status(200).json({ message: 'Settings saved', settings: settings.settings });
    } catch (error) {
        res.status(500).json({ error: 'Failed to save settings.' });
    }
});

const adminRouter = express.Router();
adminRouter.use(authenticateToken, (req, res, next) => {
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    next();
});

adminRouter.get('/users', async (req, res) => {
    const { companyId, role } = req.user;
    try {
        const queryOptions = { attributes: { exclude: ['password'] }, include: [{ model: db.Company, attributes: ['name'] }], order: [['username', 'ASC']] };
        if (role === 'admin') {
            queryOptions.where = { companyId: companyId, role: {[Op.ne]: 'superadmin'} };
        }
        res.json(await db.User.findAll(queryOptions));
    } catch (error) {
        res.status(500).json({ error: 'Failed to fetch users.' });
    }
});

adminRouter.get('/companies', async (req, res) => {
    if (req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    try {
        res.json(await db.Company.findAll({ order: [['name', 'ASC']] }));
    } catch (error) {
        res.status(500).json({ error: 'Failed to fetch companies.' });
    }
});

app.use('/api/admin', adminRouter);

const eventsRouter = express.Router();
eventsRouter.use(authenticateToken);

// --- Role & Date Filtering --- 
const applySecurityAndDateFilters = async (req, whereClause) => {
    const { role, userId: currentUserId, companyId: currentUserCompanyId } = req.user;
    const { startDate, endDate, userId: queryUserId, companyId: queryCompanyId } = req.query;

    if (startDate) {
        const start = new Date(startDate); start.setHours(0, 0, 0, 0);
        whereClause.startTime = { [Op.gte]: start };
    }
    if (endDate) {
        const end = new Date(endDate); end.setHours(23, 59, 59, 999);
        whereClause.startTime = { ...(whereClause.startTime || {}), [Op.lte]: end };
    }

    switch (role) {
        case 'superadmin':
            if (queryCompanyId && queryCompanyId !== 'all') {
                const users = await db.User.findAll({ where: { companyId: queryCompanyId }, attributes: ['id'] });
                whereClause.userId = { [Op.in]: users.map(u => u.id) };
            }
            if (queryUserId && queryUserId !== 'all') whereClause.userId = queryUserId;
            break;
        case 'admin':
            const usersInCompany = await db.User.findAll({ where: { companyId: currentUserCompanyId }, attributes: ['id'] });
            const userIdsInCompany = usersInCompany.map(u => u.id);
            whereClause.userId = { [Op.in]: userIdsInCompany };
            if (queryUserId && queryUserId !== 'all' && userIdsInCompany.includes(queryUserId)) {
                whereClause.userId = queryUserId;
            }
            break;
        default: whereClause.userId = currentUserId; break;
    }
};

// --- Workday Events ---
eventsRouter.get('/workday-events', async (req, res) => {
    try {
        const where = {};
        await applySecurityAndDateFilters(req, where);
        const events = await db.WorkdayEvent.findAll({ where, include: [{ model: db.User, attributes: ['username', 'realName'] }], order: [['startTime', 'DESC']] });
        res.json(events);
    } catch (error) {
        res.status(500).json({ error: 'Failed to fetch workday events.' });
    }
});

eventsRouter.post('/workday-events', async (req, res) => {
    try {
        const { role, userId: currentUserId, companyId: currentUserCompanyId } = req.user;
        let { userId, companyId, eventType, startTime, ...rest } = req.body;

        if (role === 'user') userId = currentUserId;
        else if (!userId) return res.status(400).json({ error: 'User selection is required.' });

        const targetUser = await db.User.findByPk(userId);
        if (!targetUser) return res.status(404).json({ error: 'Target user not found.' });
        if (role === 'admin' && targetUser.companyId !== currentUserCompanyId) {
            return res.status(403).json({ error: 'Forbidden: User is not in your company.' });
        }
        companyId = targetUser.companyId;

        if (!startTime) return res.status(400).json({ error: 'Date/Start Time is required.' });
        if (!eventType) eventType = 'workday';

        let eventData;
        if (['vacation', 'sick_leave', 'paid_holiday'].includes(eventType)) {
            const date = new Date(startTime);
            eventData = { userId, companyId, eventType, startTime: new Date(date.setHours(0, 0, 0, 0)), endTime: new Date(date.setHours(23, 59, 59, 999)) };
        } else {
            eventData = { userId, companyId, eventType: 'workday', startTime, ...rest };
        }
        const newEvent = await db.WorkdayEvent.create(eventData);
        res.status(201).json(newEvent);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// --- Refuel Events ---
eventsRouter.get('/refuel-events', async (req, res) => {
    const where = {};
    await applySecurityAndDateFilters(req, where); // Re-uses the same logic for security
    // Adjust date filter from startTime to timestamp for refuel events
    if (where.startTime) {
        where.timestamp = where.startTime;
        delete where.startTime;
    }
    try {
        const events = await db.RefuelEvent.findAll({ where, include: [{ model: db.User, attributes: ['username', 'realName'] }], order: [['timestamp', 'DESC']] });
        res.json(events);
    } catch (e) {
        res.status(500).json({ error: 'Failed to fetch refuel events.' });
    }
});

eventsRouter.post('/refuel-events', async (req, res) => {
    try {
        const { role, userId: currentUserId, companyId: currentUserCompanyId } = req.user;
        let { userId, companyId, ...eventData } = req.body;

        if (role === 'user') userId = currentUserId;
        else if (!userId) return res.status(400).json({ error: 'User selection is required.' });

        const targetUser = await db.User.findByPk(userId);
        if (!targetUser) return res.status(404).json({ error: 'Target user not found.' });
        if (role === 'admin' && targetUser.companyId !== currentUserCompanyId) {
            return res.status(403).json({ error: 'Forbidden: User is not in your company.' });
        }
        companyId = targetUser.companyId;

        const newEvent = await db.RefuelEvent.create({ userId, companyId, ...eventData });
        res.status(201).json(newEvent);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// --- Single Event generic middleware ---
const findAndAuthorizeEvent = (modelName) => async (req, res, next) => {
    try {
        const { role, userId: currentUserId, companyId: currentUserCompanyId } = req.user;
        const event = await db[modelName].findByPk(req.params.id);
        if (!event) return res.status(404).json({ error: 'Event not found.' });

        if (role === 'user' && event.userId !== currentUserId) return res.status(403).json({ error: 'Forbidden' });
        if (role === 'admin') {
            const user = await db.User.findByPk(event.userId);
            if (user.companyId !== currentUserCompanyId) return res.status(403).json({ error: 'Forbidden' });
        }
        req.event = event;
        next();
    } catch (error) {
        res.status(500).json({ error: 'Authorization error.' });
    }
};

eventsRouter.get('/workday-events/:id', findAndAuthorizeEvent('WorkdayEvent'), (req, res) => res.json(req.event));
eventsRouter.put('/workday-events/:id', findAndAuthorizeEvent('WorkdayEvent'), async (req, res) => { 
    await req.event.update(req.body); 
    res.json(req.event); 
});
eventsRouter.delete('/workday-events/:id', findAndAuthorizeEvent('WorkdayEvent'), async (req, res) => { 
    await req.event.destroy(); 
    res.status(204).send(); 
});

eventsRouter.get('/refuel-events/:id', findAndAuthorizeEvent('RefuelEvent'), (req, res) => res.json(req.event));
eventsRouter.put('/refuel-events/:id', findAndAuthorizeEvent('RefuelEvent'), async (req, res) => { 
    await req.event.update(req.body); 
    res.json(req.event); 
});
eventsRouter.delete('/refuel-events/:id', findAndAuthorizeEvent('RefuelEvent'), async (req, res) => { 
    await req.event.destroy(); 
    res.status(204).send(); 
});

app.use('/api', eventsRouter);

console.log('API routes configured.');

app.listen(PORT, async () => {
  console.log(`Server listening on port ${PORT}`);
  try {
    await db.sequelize.authenticate();
    console.log('Database connection established successfully.');
    await db.sequelize.sync({ alter: true });
    console.log('All models were synchronized successfully.');
  } catch (error) {
    console.error('Unable to connect to the database or sync models:', error);
  }
});