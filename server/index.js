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

app.get('/api/my-settings', authenticateToken, async (req, res) => {
    try {
        const settings = await db.Settings.findOne({ where: { companyId: req.user.companyId } });
        const finalSettings = mergeSettings(settings ? settings.settings : {});
        res.json(finalSettings);
    } catch (error) {
        console.error('Error loading my-settings:', error);
        res.status(500).json({ error: 'Failed to load company settings.' });
    }
});

app.get('/api/settings', authenticateToken, async (req, res) => {
    const { role, companyId } = req.user;
    let targetCompanyId = companyId;

    if (role === 'superadmin') {
        if (req.query.companyId) {
            targetCompanyId = req.query.companyId;
        } else {
            return res.json(defaultSettings);
        }
    } else if (role !== 'admin') {
        return res.status(403).json({ error: 'Forbidden' });
    }

    try {
        const settings = await db.Settings.findOne({ where: { companyId: targetCompanyId } });
        const finalSettings = mergeSettings(settings ? settings.settings : {});
        res.json(finalSettings);
    } catch (error) {
        console.error('Error loading settings:', error);
        res.status(500).json({ error: 'Failed to load settings.' });
    }
});

app.post('/api/settings', authenticateToken, async (req, res) => {
    const { role, companyId } = req.user;
    const { newSettings, targetCompanyId: reqCompanyId } = req.body;
    let targetCompanyId = companyId;

    if (role === 'superadmin') {
        if (!reqCompanyId) return res.status(400).json({ error: 'Company ID is required for superadmin.' });
        targetCompanyId = reqCompanyId;
    } else if (role !== 'admin') {
        return res.status(403).json({ error: 'Forbidden' });
    }

    try {
        if (newSettings.maintenance_mode !== undefined) {
            newSettings.maintenance_mode = newSettings.maintenance_mode === 'true' || newSettings.maintenance_mode === true;
        }
        if (newSettings.fields) {
            for (const key in newSettings.fields) {
                 newSettings.fields[key] = newSettings.fields[key] === 'true' || newSettings.fields[key] === true;
            }
        }

        let settings = await db.Settings.findOne({ where: { companyId: targetCompanyId } });
        if (settings) {
            const mergedSettings = mergeSettings(settings.settings);
            mergedSettings.maintenance_mode = newSettings.maintenance_mode;
            mergedSettings.currency = newSettings.currency;
            mergedSettings.fields = { ...mergedSettings.fields, ...newSettings.fields };
            settings.settings = mergedSettings;
            settings.changed('settings', true);
            await settings.save();
        } else {
            const finalSettings = mergeSettings(newSettings);
            settings = await db.Settings.create({ companyId: targetCompanyId, settings: finalSettings });
        }
        res.status(200).json({ message: 'Settings saved', settings: settings.settings });
    } catch (error) {
        console.error('Error saving settings:', error);
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
        const users = await db.User.findAll(queryOptions);
        res.json(users);
    } catch (error) {
        console.error('Error fetching users:', error);
        res.status(500).json({ error: 'Failed to fetch users.' });
    }
});

adminRouter.get('/companies', async (req, res) => {
    if (req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    try {
        const companies = await db.Company.findAll({ order: [['name', 'ASC']] });
        res.json(companies);
    } catch (error) {
        console.error('Error fetching companies:', error);
        res.status(500).json({ error: 'Failed to fetch companies.' });
    }
});

app.use('/api/admin', adminRouter);

// --- Secure Event Routers ---
const eventsRouter = express.Router();
eventsRouter.use(authenticateToken);

// --- Workday Events ---
eventsRouter.get('/workday-events', async (req, res) => {
    try {
        const { role, userId: currentUserId, companyId: currentUserCompanyId } = req.user;
        const { startDate, endDate, userId: queryUserId, companyId: queryCompanyId } = req.query;
        const where = {};

        if (startDate) {
            const filterStart = new Date(startDate);
            filterStart.setHours(0, 0, 0, 0);
            where[Op.or] = [ { endTime: { [Op.gte]: filterStart } }, { endTime: null } ];
        }
        if (endDate) {
            const filterEnd = new Date(endDate);
            filterEnd.setHours(23, 59, 59, 999);
            where.startTime = { [Op.lte]: filterEnd };
        }

        switch (role) {
            case 'superadmin':
                if (queryCompanyId && queryCompanyId !== 'all') {
                    const users = await db.User.findAll({ where: { companyId: queryCompanyId }, attributes: ['id'] });
                    where.userId = { [Op.in]: users.map(u => u.id) };
                }
                if (queryUserId && queryUserId !== 'all') where.userId = queryUserId;
                break;
            case 'admin':
                const usersInCompany = await db.User.findAll({ where: { companyId: currentUserCompanyId }, attributes: ['id'] });
                const userIdsInCompany = usersInCompany.map(u => u.id);
                where.userId = { [Op.in]: userIdsInCompany };
                if (queryUserId && queryUserId !== 'all' && userIdsInCompany.includes(queryUserId)) {
                    where.userId = queryUserId;
                }
                break;
            default: where.userId = currentUserId; break;
        }
        const events = await db.WorkdayEvent.findAll({ where, include: [{ model: db.User, attributes: ['username', 'realName'] }], order: [['startTime', 'DESC']] });
        res.json(events);
    } catch (error) {
        console.error('Error fetching workday events:', error);
        res.status(500).json({ error: 'Failed to fetch workday events.' });
    }
});

eventsRouter.post('/workday-events', async (req, res) => {
    try {
        const { role, userId: currentUserId, companyId: currentUserCompanyId } = req.user;
        let eventData = req.body;

        let targetCompanyId = currentUserCompanyId;
        if (role === 'user') {
            eventData.userId = currentUserId;
        } else if (role === 'admin' || role === 'superadmin') {
            if (!eventData.userId) return res.status(400).json({ error: 'User selection is required.' });
            const targetUser = await db.User.findByPk(eventData.userId);
            if (!targetUser) return res.status(404).json({ error: 'Target user not found.' });
            if (role === 'admin' && targetUser.companyId !== currentUserCompanyId) {
                return res.status(403).json({ error: 'Forbidden: You can only create events for users in your company.' });
            }
            targetCompanyId = targetUser.companyId;
        }
        eventData.companyId = targetCompanyId;

        const { eventType, startTime } = eventData;

        if (['vacation', 'sick_leave', 'paid_holiday'].includes(eventType)) {
            if (!startTime) return res.status(400).json({ error: 'Date is required for absence/holiday events.' });
            const date = new Date(startTime);
            if (isNaN(date.getTime())) return res.status(400).json({ error: 'Invalid date provided.' });
            
            eventData = {
                userId: eventData.userId, companyId: eventData.companyId, eventType: eventType,
                startTime: new Date(date.setHours(0, 0, 0, 0)),
                endTime: new Date(date.setHours(23, 59, 59, 999)),
                comment: eventData.comment || null
            };
        } else { 
            if (!startTime) return res.status(400).json({ error: 'Start time is required for workday events.' });
            eventData.eventType = 'workday';
        }

        const newEvent = await db.WorkdayEvent.create(eventData);
        res.status(201).json(newEvent);
    } catch (error) {
        if (error.name === 'SequelizeValidationError') return res.status(400).json({ error: error.errors.map(e => e.message).join(', ') });
        console.error('Error creating workday event:', error);
        res.status(500).json({ error: 'Failed to create workday event.' });
    }
});

// --- Refuel Events ---
eventsRouter.get('/refuel-events', async (req, res) => {
    try {
        const { role, userId: currentUserId, companyId: currentUserCompanyId } = req.user;
        const { startDate, endDate, userId: queryUserId, companyId: queryCompanyId } = req.query;
        const where = {};

        if (startDate) {
            const start = new Date(startDate); start.setHours(0, 0, 0, 0);
            where.timestamp = { [Op.gte]: start };
        }
        if (endDate) {
            const end = new Date(endDate); end.setHours(23, 59, 59, 999);
            where.timestamp = { ...(where.timestamp || {}), [Op.lte]: end };
        }

        switch (role) {
             case 'superadmin':
                if (queryCompanyId && queryCompanyId !== 'all') {
                    const users = await db.User.findAll({ where: { companyId: queryCompanyId }, attributes: ['id'] });
                    where.userId = { [Op.in]: users.map(u => u.id) };
                }
                if (queryUserId && queryUserId !== 'all') where.userId = queryUserId;
                break;
            case 'admin':
                const usersInCompany = await db.User.findAll({ where: { companyId: currentUserCompanyId }, attributes: ['id'] });
                const userIdsInCompany = usersInCompany.map(u => u.id);
                where.userId = { [Op.in]: userIdsInCompany };
                if (queryUserId && queryUserId !== 'all' && userIdsInCompany.includes(queryUserId)) {
                    where.userId = queryUserId;
                }
                break;
            default: where.userId = currentUserId; break;
        }
        const events = await db.RefuelEvent.findAll({ where, include: [{ model: db.User, attributes: ['username', 'realName'] }], order: [['timestamp', 'DESC']] });
        res.json(events);
    } catch (error) {
        console.error('Error fetching refuel events:', error);
        res.status(500).json({ error: 'Failed to fetch refuel events.' });
    }
});

eventsRouter.post('/refuel-events', async (req, res) => {
    try {
        const { role, userId: currentUserId, companyId: currentUserCompanyId } = req.user;
        const eventData = req.body;

        let targetCompanyId = currentUserCompanyId;
        if (role === 'user') {
            eventData.userId = currentUserId;
        } else if (role === 'admin' || role === 'superadmin') {
            if (!eventData.userId) return res.status(400).json({ error: 'User selection is required.' });
            const targetUser = await db.User.findByPk(eventData.userId);
            if (!targetUser) return res.status(404).json({ error: 'Target user not found.' });
            if (role === 'admin' && targetUser.companyId !== currentUserCompanyId) {
                return res.status(403).json({ error: 'Forbidden: You can only create events for users in your company.' });
            }
            targetCompanyId = targetUser.companyId;
        }
        eventData.companyId = targetCompanyId;
        
        if (!eventData.timestamp || !eventData.fuelAmount || !eventData.odometer) {
            return res.status(400).json({ error: 'Date, fuel amount, and odometer are required for refuel events.' });
        }

        const newEvent = await db.RefuelEvent.create(eventData);
        res.status(201).json(newEvent);
    } catch (error) {
         if (error.name === 'SequelizeValidationError') return res.status(400).json({ error: error.errors.map(e => e.message).join(', ') });
        console.error('Error creating refuel event:', error);
        res.status(500).json({ error: 'Failed to create refuel event.' });
    }
});

// Common PUT and DELETE logic for brevity in this example
const genericEditOrDelete = (modelName) => async (req, res) => {
    try {
        const { role, userId: currentUserId, companyId: currentUserCompanyId } = req.user;
        const event = await db[modelName].findByPk(req.params.id);
        if (!event) return res.status(404).json({ error: 'Event not found.' });

        if (role === 'user' && event.userId !== currentUserId) return res.status(403).json({ error: 'Forbidden' });
        if (role === 'admin') {
            const targetUser = await db.User.findByPk(event.userId);
            if (!targetUser || targetUser.companyId !== currentUserCompanyId) return res.status(403).json({ error: 'Forbidden' });
        }

        if (req.method === 'PUT') {
            await event.update(req.body);
            res.json(event);
        } else { // DELETE
            await event.destroy();
            res.status(204).send();
        }
    } catch (error) {
        console.error(`Error ${req.method === 'PUT' ? 'updating' : 'deleting'} ${modelName}:`, error);
        res.status(500).json({ error: `Failed to ${req.method === 'PUT' ? 'update' : 'delete'} event.` });
    }
};

eventsRouter.get('/workday-events/:id', genericEditOrDelete('WorkdayEvent'));
eventsRouter.put('/workday-events/:id', genericEditOrDelete('WorkdayEvent'));
eventsRouter.delete('/workday-events/:id', genericEditOrDelete('WorkdayEvent'));

eventsRouter.get('/refuel-events/:id', genericEditOrDelete('RefuelEvent'));
eventsRouter.put('/refuel-events/:id', genericEditOrDelete('RefuelEvent'));
eventsRouter.delete('/refuel-events/:id', genericEditOrDelete('RefuelEvent'));

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