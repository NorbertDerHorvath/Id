const { Sequelize, DataTypes } = require('sequelize');
const sequelize = new Sequelize(process.env.DB_CONNECTION_STRING || 'postgres://user:password@localhost:5432/database', {
  dialect: 'postgres',
  logging: false,
});

const Company = require('./Company')(sequelize, DataTypes);
const User = require('./User')(sequelize, DataTypes);
const WorkdayEvent = require('./WorkdayEvent')(sequelize, DataTypes);
const RefuelEvent = require('./RefuelEvent')(sequelize, DataTypes);
const LoadingEvent = require('./LoadingEvent')(sequelize, DataTypes);
const Settings = require('./Settings')(sequelize, DataTypes);

// Associations
Company.hasMany(User, { foreignKey: 'companyId' });
User.belongsTo(Company, { foreignKey: 'companyId' });

User.hasMany(WorkdayEvent, { foreignKey: 'userId' });
WorkdayEvent.belongsTo(User, { foreignKey: 'userId' });

User.hasMany(RefuelEvent, { foreignKey: 'userId' });
RefuelEvent.belongsTo(User, { foreignKey: 'userId' });

User.hasMany(LoadingEvent, { foreignKey: 'userId' });
LoadingEvent.belongsTo(User, { foreignKey: 'userId' });

Company.hasOne(Settings, { foreignKey: 'companyId' });
Settings.belongsTo(Company, { foreignKey: 'companyId' });

User.hasOne(Settings, { foreignKey: 'userId' });
Settings.belongsTo(User, { foreignKey: 'userId' });


module.exports = {
  sequelize,
  Company,
  User,
  WorkdayEvent,
  RefuelEvent,
  LoadingEvent,
  Settings,
};