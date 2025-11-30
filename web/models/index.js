const sequelize = require('../config/database');
const Company = require('./Company');
const User = require('./User');
const WorkdayEvent = require('./WorkdayEvent');
const RefuelEvent = require('./RefuelEvent');
const LoadingEvent = require('./LoadingEvent');

// Modellek
const db = {
  sequelize,
  Sequelize: sequelize.Sequelize,
  Company,
  User,
  WorkdayEvent,
  RefuelEvent,
  LoadingEvent,
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

module.exports = db;
