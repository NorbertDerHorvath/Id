const express = require('express');
const cors = require('cors');
const { sequelize, Company, User, WorkdayEvent, RefuelEvent, LoadingEvent } = require('./models');
const { Op } = require('sequelize'); // <--- HELYES IMPORT
const jwt = require('jsonwebtoken');
const authenticateToken = require('./middleware/authenticateToken');
const bcrypt = require('bcryptjs');

const app = express();
const PORT = process.env.PORT || 8080;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static(__dirname));

// --- HTML Serving ---
app.get('/', (req, res) => { res.sendFile(__dirname + '/login.html'); });
app.get('/dashboard', (req, res) => { res.sendFile(__dirname + '/index.html'); });
app.get('/admin', (req, res) => { res.sendFile(__dirname + '/admin.html'); });
app.get('/edit_workday.html', (req, res) => { res.sendFile(__dirname + '/edit_workday.html'); });
app.get('/edit_refuel.html', (req, res) => { res.sendFile(__dirname + '/edit_refuel.html'); });

// --- Public API Routes ---
app.post('/api/register', async (req, res) => {
    const { username, password, role, companyName, adminEmail, realName } = req.body;
    if (!username || !password || !role) return res.status(400).json({ error: 'Username, password, and role are required.' });

    try {
        const existingUser = await User.findOne({ where: { username } });
        if (existingUser) return res.status(409).json({ error: 'Username already taken.' });

        let companyId = null;
        if (role === 'admin' && companyName) {
            if (!adminEmail) return res.status(400).json({ error: 'Admin email is required for new admins.' });
            const [company] = await Company.findOrCreate({ where: { name: companyName }, defaults: { name: companyName, adminEmail: adminEmail } });
            companyId = company.id;
        } else if (role === 'user') {
            return res.status(403).json({ error: 'Users must be created by an Admin/Superadmin via the admin panel.' });
        }

        const newUser = await User.create({ username, password, role, companyId, realName: realName || username, permissions: role === 'admin' ? ['CAN_MANAGE_USERS'] : [] });
        res.status(201).json({ message: 'Admin registered successfully.', userId: newUser.id, username: newUser.username, role: newUser.role });

    } catch (error) {
        console.error('Registration error:', error);
        res.status(500).json({ error: 'Server error during registration.' });
    }
});

app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const user = await User.findOne({ where: { username } });
    if (!user || !(await user.isValidPassword(password))) return res.status(401).json({ error: 'Invalid username or password.' });
    
    const token = jwt.sign({ userId: user.id, role: user.role, companyId: user.companyId }, process.env.JWT_SECRET || 'a_very_secret_key_that_should_be_in_env', { expiresIn: '30d' });
    user.lastLoginTime = new Date();
    user.lastLoginLocation = req.ip;
    await user.save();
    res.json({ message: 'Login successful', token, username: user.username, role: user.role, permissions: user.permissions || [] });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Server error during login.' });
  }
});

app.post('/api/logout', (req, res) => { res.json({ message: 'Logged out successfully.' }); });
app.get('/api/validate-token', authenticateToken, (req, res) => { res.json({ valid: true }); });

// --- Data Access middleware ---
const canAccessResource = (model) => async (req, res, next) => {
    const { id } = req.params;
    const { userId, role, companyId } = req.user;
    try {
        const resource = await model.findByPk(id, { include: User });
        if (!resource) return res.status(404).json({ error: 'Resource not found' });
        req.resource = resource; // Attach for later use
        if (role === 'superadmin') return next();
        if (role === 'user' && resource.userId === userId) return next();
        if (role === 'admin' && resource.User && resource.User.companyId === companyId) return next();
        return res.status(403).json({ error: 'Forbidden' });
    } catch (error) {
        console.error('Authorization error:', error);
        return res.status(500).json({ error: 'Server error during authorization.' });
    }
};

// --- Workday & Refuel Events API ---
app.get('/api/workday-events', authenticateToken, async (req, res) => {
    const { startDate, endDate, carPlate } = req.query;
    const { userId, role, companyId } = req.user;
    const whereClause = {};
    try {
        if (startDate && endDate) whereClause.startTime = { [Op.between]: [new Date(startDate), new Date(endDate)] };
        if (carPlate) whereClause.carPlate = { [Op.iLike]: `%${carPlate}%` };
        if (role === 'user') whereClause.userId = userId;
        else if (role === 'admin') {
            const usersInCompany = await User.findAll({ where: { companyId }, attributes: ['id'] });
            whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
        }
        const events = await WorkdayEvent.findAll({ where: whereClause, include: User, order: [['startTime', 'DESC']] });
        res.json(events);
    } catch (e) { res.status(500).json({error: e.message}) }
});
app.post('/api/workday-events', authenticateToken, async (req, res) => {
  try {
    const event = await WorkdayEvent.create({ ...req.body, userId: req.user.userId });
    res.status(201).json(await WorkdayEvent.findByPk(event.id, { include: User }));
  } catch (error) { res.status(500).json({ error: 'Failed to save workday event.' }); }
});
app.get('/api/workday-events/:id', authenticateToken, canAccessResource(WorkdayEvent), (req, res) => { res.json(req.resource); });
app.put('/api/workday-events/:id', authenticateToken, canAccessResource(WorkdayEvent), async (req, res) => {
    try {
        await req.resource.update(req.body);
        res.json(req.resource);
    } catch (e) { res.status(500).json({error: e.message})}
});
app.delete('/api/workday-events/:id', authenticateToken, canAccessResource(WorkdayEvent), async (req, res) => {
    try {
        await req.resource.destroy();
        res.status(204).send();
    } catch (e) { res.status(500).json({error: e.message})}
});
app.get('/api/refuel-events', authenticateToken, async (req, res) => {
    const { startDate, endDate, carPlate } = req.query;
    const { userId, role, companyId } = req.user;
    const whereClause = {};
    try {
        if (startDate && endDate) whereClause.timestamp = { [Op.between]: [new Date(startDate), new Date(endDate)] };
        if (carPlate) whereClause.carPlate = { [Op.iLike]: `%${carPlate}%` };
        if (role === 'user') whereClause.userId = userId;
        else if (role === 'admin') {
            const usersInCompany = await User.findAll({ where: { companyId }, attributes: ['id'] });
            whereClause.userId = { [Op.in]: usersInCompany.map(u => u.id) };
        }
        const events = await RefuelEvent.findAll({ where: whereClause, include: User, order: [['timestamp', 'DESC']] });
        res.json(events);
    } catch (e) { res.status(500).json({error: e.message}) }
});
app.post('/api/refuel-events', authenticateToken, async (req, res) => {
    try {
        const event = await RefuelEvent.create({ ...req.body, userId: req.user.userId });
        res.status(201).json(await RefuelEvent.findByPk(event.id, { include: User }));
    } catch (error) { res.status(500).json({ error: 'Failed to save refuel event.' }); }
});
app.get('/api/refuel-events/:id', authenticateToken, canAccessResource(RefuelEvent), (req, res) => { res.json(req.resource); });
app.put('/api/refuel-events/:id', authenticateToken, canAccessResource(RefuelEvent), async (req, res) => {
    try {
        await req.resource.update(req.body);
        res.json(await RefuelEvent.findByPk(req.params.id, { include: User }));
    } catch(e) { res.status(500).json({error: e.message}) }
});
app.delete('/api/refuel-events/:id', authenticateToken, canAccessResource(RefuelEvent), async (req, res) => {
    try {
        await req.resource.destroy();
        res.status(204).send();
    } catch(e) { res.status(500).json({error: e.message}) }
});

// --- Admin Routes ---
const adminRouter = express.Router();
adminRouter.use(authenticateToken, (req, res, next) => {
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden: Admins or Superadmin only.' });
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
    const { username, password, role, companyName, realName } = req.body;
    const { role: adminRole, companyId: adminCompanyId } = req.user;
    if (!username || !password) return res.status(400).json({ error: 'Username and password are required.' });

    let finalCompanyId = adminRole === 'admin' ? adminCompanyId : null;
    let finalRole = role || 'user';

    if (adminRole === 'admin' && finalRole === 'superadmin') return res.status(403).json({ error: 'Admins cannot create superadmins.' });

    if (companyName) {
        try {
            const [company] = await Company.findOrCreate({ where: { name: companyName }, defaults: { name: companyName, adminEmail: `${username}@company.com` } });
            finalCompanyId = company.id;
        } catch(e) { return res.status(500).json({ error: 'Could not create or find company.' }); }
    }

    if (!finalCompanyId && finalRole !== 'superadmin') return res.status(400).json({ error: 'Company assignment is required.' });

    try {
        const newUser = await User.create({ username, password, realName, role: finalRole, companyId: finalCompanyId });
        const { password: _, ...userResponse } = newUser.get({ plain: true });
        res.status(201).json({ message: 'User created successfully', user: userResponse });
    } catch (error) {
        if (error.name === 'SequelizeUniqueConstraintError') return res.status(409).json({ error: 'Username already exists.' });
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

        if (userToUpdate.role === 'superadmin' && adminRole !== 'superadmin') return res.status(403).json({ error: 'You do not have permission to edit a superadmin.'});

        if (adminRole === 'admin') {
            if (userToUpdate.companyId !== adminCompanyId) return res.status(403).json({ error: 'Forbidden: You can only edit users in your own company.' });
            if (role === 'superadmin') return res.status(403).json({ error: 'Admins cannot assign superadmin role.' });
        }

        if (password) userToUpdate.password = await bcrypt.hash(password, 10);
        if (realName) userToUpdate.realName = realName;
        if (role) userToUpdate.role = role;
        
        if (companyName && (adminRole === 'superadmin' || (adminRole === 'admin' && userToUpdate.companyId === adminCompanyId))) {
            const [company] = await Company.findOrCreate({ where: { name: companyName }, defaults: { name: companyName, adminEmail: `${userToUpdate.username}@company.com` } });
            userToUpdate.companyId = company.id;
        }
        
        await userToUpdate.save();
        const { password: _, ...userResponse } = userToUpdate.get({ plain: true });
        res.json({ message: 'User updated successfully.', user: userResponse });
    } catch (error) { res.status(500).json({ error: 'Failed to update user.' }); }
});

adminRouter.get('/debug-dump', async (req, res) => {
    if (req.user.role !== 'superadmin') {
        return res.status(403).json({ error: 'Forbidden: Superadmin only.' });
    }
    try {
        const users = await User.findAll({
            attributes: ['id', 'username', 'role', 'companyId', 'realName'],
            include: { model: Company, attributes: ['id', 'name'] },
            order: [['companyId', 'ASC'], ['username', 'ASC']]
        });
        const companies = await Company.findAll({order: [['name', 'ASC']]});
        res.json({ users, companies });
    } catch (error) {
        console.error('Error during debug dump:', error);
        res.status(500).json({ error: 'Failed to generate debug dump.' });
    }
});

adminRouter.get('/merge-companies', async (req, res) => {
    if (req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden: Superadmin only.' });
    const transaction = await sequelize.transaction();
    try {
        const allCompanies = await Company.findAll({ attributes: ['id', 'name'], transaction });
        const nameMap = new Map();
        for (const company of allCompanies) {
            if (!nameMap.has(company.name)) nameMap.set(company.name, []);
            nameMap.get(company.name).push(company);
        }

        let companiesMerged = 0, usersUpdated = 0;
        for (const [name, companies] of nameMap.entries()) {
            if (companies.length > 1) {
                const masterCompany = companies.shift();
                const duplicateCompanyIds = companies.map(c => c.id);
                const [updateCount] = await User.update({ companyId: masterCompany.id }, { where: { companyId: { [Op.in]: duplicateCompanyIds } }, transaction });
                usersUpdated += updateCount;
                await Company.destroy({ where: { id: { [Op.in]: duplicateCompanyIds } }, transaction });
                companiesMerged += duplicateCompanyIds.length;
            }
        }
        await transaction.commit();
        res.status(200).send(`Company merge complete. Merged ${companiesMerged} duplicate companies and updated ${usersUpdated} users.`);
    } catch (error) {
        await transaction.rollback();
        console.error('Error merging companies:', error);
        res.status(500).json({ error: 'An error occurred while merging companies.' });
    }
});

app.use('/api/admin', adminRouter);

// --- Server Initialization ---
const createSuperAdmin = async () => {
    try {
        const superadmin = await User.findOne({ where: { username: 'norbapp' } });
        if (!superadmin) {
            await User.create({ username: 'norbapp', password: 'norbapp', role: 'superadmin', realName: 'Super Admin', companyId: null, permissions: ['ALL'] });
            console.log('Superadmin user "norbapp" created.');
        } else {
             if (superadmin.companyId !== null) {
                superadmin.companyId = null;
                await superadmin.save();
                console.log('Superadmin user detached from any company.');
             }
             if (!(await superadmin.isValidPassword('norbapp'))) {
                 superadmin.password = await bcrypt.hash('norbapp', 10);
                 await superadmin.save();
                 console.log('Superadmin password has been reset.');
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
    console.log('All models synchronized.');
    await createSuperAdmin();
  } catch (error) { console.error('Unable to connect to the database:', error); }
});
