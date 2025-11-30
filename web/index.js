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

// --- Protected Routes (require authentication) ---

// Middleware is applied to all routes below this point
app.use(authenticateToken);

// 3. Create a new driver for the admin's company
app.post('/api/users/driver', async (req, res) => {
  // The user's role and companyId come from the authenticated token (req.user)
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
      companyId: req.user.companyId, // Associate with the admin's company
    });
    res.status(201).json({ message: 'Driver created successfully.', userId: newDriver.id, username: newDriver.username });
  } catch (error) {
    if (error.name === 'SequelizeUniqueConstraintError') {
      return res.status(409).json({ error: 'Username already exists.' });
    }
    res.status(500).json({ error: 'Server error creating driver.' });
  }
});

// 4. Data submission endpoints
app.post('/api/workday-events', async (req, res) => {
  try {
    const event = await WorkdayEvent.create({ ...req.body, userId: req.user.userId });
    res.status(201).json(event);
  } catch (error) {
    console.error('Error saving workday event:', error);
    res.status(500).json({ error: 'Failed to save workday event.' });
  }
});

app.post('/api/refuel-events', async (req, res) => {
  try {
    const event = await RefuelEvent.create({ ...req.body, userId: req.user.userId });
    res.status(201).json(event);
  } catch (error) {
    console.error('Error saving refuel event:', error);
    res.status(500).json({ error: 'Failed to save refuel event.' });
  }
});

app.post('/api/loading-events', async (req, res) => {
  try {
    const event = await LoadingEvent.create({ ...req.body, userId: req.user.userId });
    res.status(201).json(event);
  } catch (error) {
    console.error('Error saving loading event:', error);
    res.status(500).json({ error: 'Failed to save loading event.' });
  }
});

// TODO: Add GET endpoints for admins to retrieve and filter data

// Start server and sync database
app.listen(PORT, async () => {
  console.log(`Server listening on port ${PORT}`);
  try {
    await sequelize.authenticate();
    console.log('Database connection has been established successfully.');
    // Sync all models. Use { force: true } only in dev to drop and re-create tables.
    await sequelize.sync({ alter: true }); 
    console.log('All models were synchronized successfully.');
  } catch (error) {
    console.error('Unable to connect to the database:', error);
  }
});
