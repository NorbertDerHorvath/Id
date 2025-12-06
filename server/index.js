const express = require('express');
const cors = require('cors');
const { sequelize, Company, User, WorkdayEvent, RefuelEvent, LoadingEvent, Op } = require('./models');
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

app.get('/edit_workday.html', (req, res) => {
  res.sendFile(__dirname + '/edit_workday.html');
});

app.get('/edit_refuel.html', (req, res) => {
  res.sendFile(__dirname + '/edit_refuel.html');
});


// --- Public API Routes ---

// Helper Route to create/reset the test user for debugging
app.get('/api/create-test-user', async (req, res) => {
  try {
    const [company] = await Company.findOrCreate({
      where: { name: 'Test Company' },
      defaults: { adminEmail: 'admin@test.com' }
    });
    await User.destroy({ where: { username: 'norbi' } });
    await User.create({
      username: 'norbi',
      password: 'norbi',
      role: 'driver',
      companyId: company.id
    });
    return res.status(201).send('Test user (re)created successfully');
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
    const token = jwt.sign(
      { userId: user.id, role: user.role, companyId: user.companyId },
      process.env.JWT_SECRET || 'a_very_secret_key_that_should_be_in_env',
      { expiresIn: '30d' }
    );
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


// --- Data API Routes (Protected or Public as needed) ---

// Workday Events
app.post('/api/workday-events', authenticateToken, async (req, res) => {
  try {
    const { id, ...eventData } = req.body;
    const event = await WorkdayEvent.create({ ...eventData, userId: req.user.userId });
    const newEvent = await WorkdayEvent.findByPk(event.id, { include: User });
    res.status(201).json(newEvent);
  } catch (error) {
    res.status(500).json({ error: 'Failed to save workday event.' });
  }
});

app.get('/api/workday-events', async (req, res) => {
    try {
        const { startDate, endDate, carPlate } = req.query;
        const whereClause = {};

        if (startDate && endDate) {
            whereClause.startTime = {
                [Op.between]: [new Date(startDate), new Date(endDate)]
            };
        }
        if (carPlate) {
            whereClause.carPlate = { [Op.iLike]: `%${carPlate}%` };
        }

        const events = await WorkdayEvent.findAll({
            where: whereClause,
            include: User,
            order: [['startTime', 'DESC']]
        });
        res.json(events);
    } catch (error) {
        res.status(500).json({ error: 'Failed to fetch workday events.' });
    }
});

app.get('/api/workday-events/:id', async (req, res) => { // Not authenticated for simplicity on the web editor
    try {
        const event = await WorkdayEvent.findByPk(req.params.id, { include: User });
        if (event) {
            res.json(event);
        } else {
            res.status(404).json({ error: 'WorkdayEvent not found' });
        }
    } catch (error) {
        res.status(500).json({ error: 'Failed to fetch workday event.' });
    }
});

app.put('/api/workday-events/:id', async (req, res) => { // Not authenticated for simplicity
    try {
        const event = await WorkdayEvent.findByPk(req.params.id);
        if (event) {
            const { id, ...eventData } = req.body;
            await event.update(eventData);
            res.json(event);
        } else {
            res.status(404).json({ error: 'WorkdayEvent not found' });
        }
    } catch (error) {
        res.status(500).json({ error: 'Failed to update workday event.' });
    }
});

app.delete('/api/workday-events/:id', async (req, res) => { // Not authenticated for simplicity
    try {
        const event = await WorkdayEvent.findByPk(req.params.id);
        if (event) {
            await event.destroy();
            res.status(204).send();
        } else {
            res.status(404).json({ error: 'WorkdayEvent not found' });
        }
    } catch (error) {
        res.status(500).json({ error: 'Failed to delete workday event.' });
    }
});

// Refuel Events
app.get('/api/refuel-events', async (req, res) => {
    try {
        const { startDate, endDate, carPlate } = req.query;
        const whereClause = {};

        if (startDate && endDate) {
            whereClause.timestamp = {
                [Op.between]: [new Date(startDate), new Date(endDate)]
            };
        }
        if (carPlate) {
            whereClause.carPlate = { [Op.iLike]: `%${carPlate}%` };
        }

        const events = await RefuelEvent.findAll({
            where: whereClause,
            include: User,
            order: [['timestamp', 'DESC']]
        });
        res.json(events);
    } catch (error) {
        res.status(500).json({ error: 'Failed to fetch refuel events.' });
    }
});

app.get('/api/refuel-events/:id', async (req, res) => {
    try {
        const event = await RefuelEvent.findByPk(req.params.id, { include: User });
        if (event) {
            res.json(event);
        } else {
            res.status(404).json({ error: 'RefuelEvent not found' });
        }
    } catch (error) {
        res.status(500).json({ error: 'Failed to fetch refuel event.' });
    }
});

app.post('/api/refuel-events', authenticateToken, async (req, res) => {
    try {
        const { ...eventData } = req.body;
        const event = await RefuelEvent.create({ ...eventData, userId: req.user.userId });
        const newEvent = await RefuelEvent.findByPk(event.id, { include: User });
        res.status(201).json(newEvent);
    } catch (error) {
        console.error("Error saving refuel event:", error);
        res.status(500).json({ error: 'Failed to save refuel event.' });
    }
});

app.put('/api/refuel-events/:id', async (req, res) => {
    try {
        const event = await RefuelEvent.findByPk(req.params.id);
        if (event) {
            const { id, ...eventData } = req.body;
            await event.update(eventData);
            const updatedEvent = await RefuelEvent.findByPk(req.params.id, { include: User });
            res.json(updatedEvent);
        } else {
            res.status(404).json({ error: 'RefuelEvent not found' });
        }
    } catch (error) {
        console.error("Error updating refuel event:", error);
        res.status(500).json({ error: 'Failed to update refuel event.' });
    }
});

app.delete('/api/refuel-events/:id', async (req, res) => {
    try {
        const event = await RefuelEvent.findByPk(req.params.id);
        if (event) {
            await event.destroy();
            res.status(204).send();
        } else {
            res.status(404).json({ error: 'RefuelEvent not found' });
        }
    } catch (error) {
        res.status(500).json({ error: 'Failed to delete refuel event.' });
    }
});

// Start server and sync database
app.listen(PORT, async () => {
  console.log(`Server listening on port ${PORT}`);
  try {
    await sequelize.authenticate();
    console.log('Database connection established.');
    await sequelize.sync({ alter: true }); 
    console.log('All models synchronized.');
  } catch (error) {
    console.error('Unable to connect to the database:', error);
  }
});
