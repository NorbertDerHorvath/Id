const express = require('express');
const cors = require('cors');
const { sequelize, Company, User, WorkdayEvent, RefuelEvent, LoadingEvent } = require('./models');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const authenticateToken = require('./middleware/authenticateToken');

const app = express();
const PORT = process.env.PORT || 8080;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static('public')); // Serve static files from 'public' directory

// --- HTML Serving ---
app.get('/', (req, res) => {
  res.sendFile(__dirname + '/index.html');
});


// --- Public API Routes ---

// Helper Route to create/reset the test user for debugging
app.get('/api/create-test-user', async (req, res) => {
  try {
    // Ensure a default company exists, and get its instance
    const [company, wasCompanyCreated] = await Company.findOrCreate({
      where: { name: 'Test Company' },
      defaults: { adminEmail: 'admin@test.com' }
    });

    // Destroy the user if it already exists to ensure a clean slate
    await User.destroy({ where: { username: 'norbi' } });

    // Create the new test user, referencing the company's actual ID
    const user = await User.create({
      username: 'norbi',
      password: 'norbi', // The beforeCreate hook will hash this
      role: 'driver',
      companyId: company.id // Use the retrieved company's ID
    });

    return res.status(201).send('Test user (re)created successfully with username: norbi, password: norbi');

  } catch (error) {
    console.error('Error creating test user:', error);
    return res.status(500).send('Error creating test user: ' + error.message);
  }
});

// Login user
app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;

  try {
    const user = await User.findOne({ where: { username } });
    if (!user || !(await user.isValidPassword(password))) {
      return res.status(401).json({ error: 'Invalid username or password.' });
    }

    // Sign a JWT token
    const token = jwt.sign(
      { userId: user.id, role: user.role, companyId: user.companyId },
      process.env.JWT_SECRET || 'a_very_secret_key_that_should_be_in_env',
      { expiresIn: '30d' } // Token expires in 30 days
    );
    
    // Update last login info
    user.lastLoginTime = new Date();
    user.lastLoginLocation = req.ip;
    await user.save();

    res.json({ message: 'Login successful', token, username: user.username });

  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Server error during login.' });
  }
});

app.get('/api/validate-token', authenticateToken, (req, res) => {
    res.json({ valid: true });
});

// Get last login info
app.get('/api/last-login', async (req, res) => {
  try {
    const user = await User.findOne({
      order: [['lastLoginTime', 'DESC']]
    });
    if (user) {
      res.json({ time: user.lastLoginTime, location: user.lastLoginLocation });
    } else {
      res.json({ time: 'N/A', location: 'N/A' });
    }
  } catch (error) {
    console.error('Error fetching last login:', error);
    res.status(500).json({ error: 'Failed to fetch last login.' });
  }
});

// --- Data Submission & Retrieval API Routes (Protected) ---

// Workday Events
app.post('/api/workday-events', authenticateToken, async (req, res) => {
  try {
    const eventData = req.body;
    if (!eventData.startTime) {
        eventData.startTime = new Date();
    }
    const event = await WorkdayEvent.create({ ...eventData, userId: req.user.userId });
    res.status(201).json(event);
  } catch (error) {
    console.error('Error saving workday event:', error);
    res.status(500).json({ error: 'Failed to save workday event.' });
  }
});

app.get('/api/workday-events', async (req, res) => {
  try {
    const events = await WorkdayEvent.findAll({ include: User });
    res.json(events);
  } catch (error) {
    console.error('Error fetching workday events:', error);
    res.status(500).json({ error: 'Failed to fetch workday events.' });
  }
});

// Refuel Events
app.post('/api/refuel-events', authenticateToken, async (req, res) => {
  try {
    const event = await RefuelEvent.create({ ...req.body, userId: req.user.userId });
    res.status(201).json(event);
  } catch (error) {
    console.error('Error saving refuel event:', error);
    res.status(500).json({ error: 'Failed to save refuel event.' });
  }
});

app.get('/api/refuel-events', async (req, res) => {
  try {
    const events = await RefuelEvent.findAll({ include: User });
    res.json(events);
  } catch (error) {
    console.error('Error fetching refuel events:', error);
    res.status(500).json({ error: 'Failed to fetch refuel events.' });
  }
});


// Loading Events
app.post('/api/loading-events', authenticateToken, async (req, res) => {
  try {
    const event = await LoadingEvent.create({ ...req.body, userId: req.user.userId });
    res.status(201).json(event);
  } catch (error) {
    console.error('Error saving loading event:', error);
    res.status(500).json({ error: 'Failed to save loading event.' });
  }
});

app.get('/api/loading-events', async (req, res) => {
  try {
    const events = await LoadingEvent.findAll({ include: User });
    res.json(events);
  } catch (error) {
    console.error('Error fetching loading events:', error);
    res.status(500).json({ error: 'Failed to fetch loading events.' });
  }
});


// Start server and sync database
app.listen(PORT, async () => {
  console.log(`Server listening on port ${PORT}`);
  try {
    await sequelize.authenticate();
    console.log('Database connection has been established successfully.');
    await sequelize.sync({ alter: true }); 
    console.log('All models were synchronized successfully.');
  } catch (error) {
    console.error('Unable to connect to the database:', error);
  }
});
