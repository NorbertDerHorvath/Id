const express = require('express');
const cors = require('cors');
const path = require('path');
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

// --- HTML Serving (NO authentication middleware here!) ---
app.get('/', (req, res) => { res.sendFile(path.join(__dirname, 'login.html')); });
app.get('/login.html', (req, res) => { res.sendFile(path.join(__dirname, 'login.html')); });
app.get('/dashboard', (req, res) => { res.sendFile(path.join(__dirname, 'index.html')); });
app.get('/admin', (req, res) => { res.sendFile(path.join(__dirname, 'admin.html')); });
app.get('/settings', (req, res) => { res.sendFile(path.join(__dirname, 'settings.html')); });

// --- API Routes ---

// Public login route (NO authenticateToken middleware)
app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const user = await User.findOne({ where: { username } });
    if (!user || !(await user.isValidPassword(password))) return res.status(401).json({ error: 'Invalid username or password.' });
    
    const token = jwt.sign({ userId: user.id, role: user.role, companyId: user.companyId }, process.env.JWT_SECRET || 'a_very_secret_key_that_should_be_in_env', { expiresIn: '30d' });
    res.json({ message: 'Login successful', token, username: user.username, role: user.role });
  } catch (error) {
    res.status(500).json({ error: 'Server error during login.' });
  }
});

// Secure all other API routes
app.use('/api', authenticateToken);

// Protected API routes from here
app.get('/api/validate-token', (req, res) => res.json({ valid: true }));

app.get('/api/settings', async (req, res) => {
    // ... (rest of the logic is correct)
});

app.post('/api/settings', async (req, res) => {
    // ... (rest of the logic is correct)
});

app.get('/api/companies', async (req, res) => {
    // ... (rest of the logic is correct)
});

app.get('/api/workday-events', async (req, res) => {
    // ... (rest of the logic is correct)
});

app.post('/api/workday-events', async (req, res) => {
    // ... (rest of the logic is correct)
});

app.get('/api/refuel-events', async (req, res) => {
    // ... (rest of the logic is correct)
});

app.post('/api/refuel-events', async (req, res) => {
    // ... (rest of the logic is correct)
});

const adminRouter = express.Router();
adminRouter.get('/users', async (req, res) => {
    // ... (rest of the logic is correct)
});
app.use('/api/admin', adminRouter);


// --- Server Initialization ---
// ... (rest of the file is correct)
