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
        res.json({ userSettings: userSettings?.settings || {}, companySettings: companySettings?.settings || {} });
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

// Company routes
app.get('/api/companies', authenticateToken, async (req, res) => {
    if (req.user.role !== 'superadmin') return res.status(403).json({ error: 'Forbidden' });
    try {
        const companies = await Company.findAll({ order: [['name', 'ASC']] });
        res.json(companies);
    } catch (e) {
        res.status(500).json({ error: 'Failed to fetch companies.' });
    }
});

// ... (rest of the file remains the same)

app.listen(PORT, async () => {
  console.log(`Server listening on port ${PORT}`);
  try {
    await sequelize.authenticate();
    console.log('Database connection established.');
    await sequelize.sync({ alter: true });
    // createSuperAdmin(); // This should be handled by the model logic now
    console.log('All models synchronized.');
  } catch (error) { console.error('Unable to connect to the database:', error); }
});
