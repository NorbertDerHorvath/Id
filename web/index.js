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

// --- Public Routes ---

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

// 1. Register a new Company and its Admin user
app.post('/api/register', async (req, res) => {
  const { companyName, adminEmail, password } = req.body;

  if (!companyName || !adminEmail || !password) {
    return res.status(400).json({ error: 'All fields are required.' });
  }

  const t = await sequelize.transaction();

  try {
    const company = await Company.create({ name: companyName, adminEmail }, { transaction: t });
    const adminUser = await User.create({
      username: adminEmail, // Admin's username is their email
      password,
      role: 'admin',
      companyId: company.id,
    }, { transaction: t });

    await t.commit();
    res.status(201).json({ message: 'Company and admin registered successfully.', companyId: company.id });

  } catch (error) {
    await t.rollback();
    console.error('Registration error:', error);
    if (error.name === 'SequelizeUniqueConstraintError') {
      return res.status(409).json({ error: 'Company name or email already exists.' });
    }
    res.status(500).json({ error: 'Server error during registration.' });
  }
});

// 2. Login user
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

    res.json({ message: 'Login successful', token });

  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Server error during login.' });
  }
});

// 4. Data submission endpoints
app.post('/api/workday-events', authenticateToken, async (req, res) => {
  try {
    const event = await WorkdayEvent.create({ ...req.body, userId: req.user.userId });
    res.status(201).json(event);
  } catch (error) {
    console.error('Error saving workday event:', error);
    res.status(500).json({ error: 'Failed to save workday event.' });
  }
});

app.post('/api/refuel-events', authenticateToken, async (req, res) => {
  try {
    const event = await RefuelEvent.create({ ...req.body, userId: req.user.userId });
    res.status(201).json(event);
  } catch (error) {
    console.error('Error saving refuel event:', error);
    res.status(500).json({ error: 'Failed to save refuel event.' });
  }
});

app.post('/api/loading-events', authenticateToken, async (req, res) => {
  try {
    const event = await LoadingEvent.create({ ...req.body, userId: req.user.userId });
    res.status(201).json(event);
  } catch (error) {
    console.error('Error saving loading event:', error);
    res.status(500).json({ error: 'Failed to save loading event.' });
  }
});


// --- Protected Routes (for admin actions) ---
app.post('/api/users/driver', authenticateToken, async (req, res) => {
  if (req.user.role !== 'admin') {
    return res.status(403).json({ error: 'Forbidden: Only admins can create drivers.' });
  }

  const { username, password } = req.body;
  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password are required for the new driver.' });
  }

  try {
    const newDriver = await User.create({
      username,
      password,
      role: 'driver',
      companyId: req.user.companyId,
    });
    res.status(201).json({ message: 'Driver created successfully.', userId: newDriver.id, username: newDriver.username });
  } catch (error) {
    if (error.name === 'SequelizeUniqueConstraintError') {
      return res.status(409).json({ error: 'Username already exists.' });
    }
    res.status(500).json({ error: 'Server error creating driver.' });
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
