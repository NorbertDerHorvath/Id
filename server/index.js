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
app.use(express.static(path.join(__dirname, 'public'))); // Serve public files

// --- HTML Serving ---
app.get('/', (req, res) => { res.sendFile(path.join(__dirname, '/login.html')); });
app.get('/dashboard', authenticateToken, (req, res) => { res.sendFile(path.join(__dirname, '/index.html')); });
app.get('/admin', authenticateToken, (req, res) => { res.sendFile(path.join(__dirname, '/admin.html')); });
app.get('/settings', authenticateToken, (req, res) => { res.sendFile(path.join(__dirname, '/settings.html')); });

// --- API Routes (all protected) ---
app.use('/api', authenticateToken); // Protect all API routes

app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const user = await User.findOne({ where: { username } });
    if (!user || !(await user.isValidPassword(password))) return res.status(401).json({ error: 'Invalid credentials.' });
    
    const token = jwt.sign({ userId: user.id, role: user.role, companyId: user.companyId }, process.env.JWT_SECRET || 'a_very_secret_key_that_should_be_in_env', { expiresIn: '30d' });
    res.json({ message: 'Login successful', token, username: user.username, role: user.role });
  } catch (error) {
    res.status(500).json({ error: 'Server error.' });
  }
});

app.get('/api/validate-token', (req, res) => res.json({ valid: true }));

// ... (rest of the API routes: /api/settings, /api/companies, /api/workday-events, etc.)

// --- Server Initialization ---
app.listen(PORT, async () => {
  console.log(`Server listening on port ${PORT}`);
  try {
    await sequelize.authenticate();
    console.log('Database connection established.');
    await sequelize.sync({ alter: true });
    // Initialization logic like createSuperAdmin()
    console.log('All models synchronized.');
  } catch (error) { console.error('Unable to connect to the database:', error); }
});
