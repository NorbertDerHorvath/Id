console.log('--- SERVER STARTING: DEPLOYMENT-CHECK-V2 ---');
const express = require('express');
const cors = require('cors');
const db = require('./models');
const { Op } = db.Sequelize; // <-- THIS WAS THE CRITICAL BUG! Correctly getting Op.
const jwt = require('jsonwebtoken');
const authenticateToken = require('./middleware/authenticateToken');
const bcrypt = require('bcryptjs');

const app = express();
const PORT = process.env.PORT || 8080;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static(__dirname));

console.log('Middleware configured.');

// HTML Serving
app.get('/', (req, res) => { res.sendFile(__dirname + '/login.html'); });
app.get('/dashboard', (req, res) => { res.sendFile(__dirname + '/index.html'); });
app.get('/admin', (req, res) => { res.sendFile(__dirname + '/admin.html'); });
app.get('/settings', (req, res) => { res.sendFile(__dirname + '/settings.html'); });
app.get('/edit_workday.html', (req, res) => { res.sendFile(__dirname + '/edit_workday.html'); });
app.get('/edit_refuel.html', (req, res) => { res.sendFile(__dirname + '/edit_refuel.html'); });

console.log('HTML routes configured.');

// --- API Routes ---

// Public routes
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

// --- Admin Settings ---
app.get('/api/settings', authenticateToken, async (req, res) => {
    console.log(`GET /api/settings request received for user: ${req.user.userId}`);
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') {
        console.log(`Forbidden GET /api/settings for user: ${req.user.userId}`);
        return res.status(403).json({ error: 'Forbidden' });
    }
    try {
        const settings = await db.Settings.findOne();
        console.log('Settings fetched from DB');
        res.json(settings ? settings.settings : {});
    } catch (error) {
        console.error('Error loading settings:', error);
        res.status(500).json({ error: 'Failed to load settings.' });
    }
});

app.post('/api/settings', authenticateToken, async (req, res) => {
    console.log(`POST /api/settings request received for user: ${req.user.userId}`);
    if (req.user.role !== 'admin' && req.user.role !== 'superadmin') {
        console.log(`Forbidden POST /api/settings for user: ${req.user.userId}`);
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
        console.log('Settings saved to DB');
        res.status(200).json({ message: 'Settings saved', settings: settings.settings });
    } catch (error) {
        console.error('Error saving settings:', error);
        res.status(500).json({ error: 'Failed to save settings.' });
    }
});

// --- Admin & Superadmin Routes ---
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

app.use('/api/admin', adminRouter);
console.log('API routes configured.');

// --- Server Initialization ---
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