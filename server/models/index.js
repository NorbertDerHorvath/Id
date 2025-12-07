const { Sequelize, DataTypes } = require('sequelize');

const connectionString = process.env.DB_CONNECTION_STRING || 'postgres://user:password@localhost:5432/database';
const isProduction = process.env.NODE_ENV === 'production';

const sequelize = new Sequelize(connectionString, {
  dialect: 'postgres',
  logging: false,
  dialectOptions: {
    ssl: isProduction ? { require: true, rejectUnauthorized: false } : false,
  },
});

const db = {};

db.Sequelize = Sequelize;
db.sequelize = sequelize;

db.Company = require('./Company')(sequelize, DataTypes);
db.User = require('./User')(sequelize, DataTypes);
db.WorkdayEvent = require('./WorkdayEvent')(sequelize, DataTypes);
db.RefuelEvent = require('./RefuelEvent')(sequelize, DataTypes);
db.LoadingEvent = require('./LoadingEvent')(sequelize, DataTypes);
db.Settings = require('./Settings')(sequelize, DataTypes);

// Associations
db.Company.hasMany(db.User, { foreignKey: 'companyId' });
db.User.belongsTo(db.Company, { foreignKey: 'companyId' });

db.User.hasMany(db.WorkdayEvent, { foreignKey: 'userId' });
db.WorkdayEvent.belongsTo(db.User, { foreignKey: 'userId' });

db.User.hasMany(db.RefuelEvent, { foreignKey: 'userId' });
db.RefuelEvent.belongsTo(db.User, { foreignKey: 'userId' });

db.User.hasMany(db.LoadingEvent, { foreignKey: 'userId' });
db.LoadingEvent.belongsTo(db.User, { foreignKey: 'userId' });

db.Company.hasOne(db.Settings, { foreignKey: 'companyId' });
db.Settings.belongsTo(db.Company, { foreignKey: 'companyId' });

db.User.hasOne(db.Settings, { foreignKey: 'userId' });
db.Settings.belongsTo(db.User, { foreignKey: 'userId' });

module.exports = db;