const sequelize = require('../config/database');
const Company = require('./Company');
const User = require('./User');
const WorkdayEvent = require('./WorkdayEvent');
const RefuelEvent = require('./RefuelEvent');
const LoadingEvent = require('./LoadingEvent');
const Settings = require('./Settings'); // Import the new model

// Modellek
const db = {
  sequelize,
  Sequelize: sequelize.Sequelize,
  Company,
  User,
  WorkdayEvent,
  RefuelEvent,
  LoadingEvent,
  Settings // Add model to the db object
};

// Relációk
Company.hasMany(User, { foreignKey: 'companyId' });
User.belongsTo(Company, { foreignKey: 'companyId' });

User.hasMany(WorkdayEvent, { foreignKey: 'userId' });
WorkdayEvent.belongsTo(User, { foreignKey: 'userId' });

User.hasMany(RefuelEvent, { foreignKey: 'userId' });
RefuelEvent.belongsTo(User, { foreignKey: 'userId' });

User.hasMany(LoadingEvent, { foreignKey: 'userId' });
LoadingEvent.belongsTo(User, { foreignKey: 'userId' });

// New Settings relationships
Company.hasOne(Settings, { foreignKey: 'companyId', as: 'companySettings' });
Settings.belongsTo(Company, { foreignKey: 'companyId' });

User.hasOne(Settings, { foreignKey: 'userId', as: 'userSettings' });
Settings.belongsTo(User, { foreignKey: 'userId' });

module.exports = db;
