'use strict';

const fs = require('fs');
const path = require('path');
const Sequelize = require('sequelize');
const basename = path.basename(__filename);
const db = {};

// Directly import the already configured sequelize instance from the custom database.js
const sequelize = require(path.join(__dirname, '..', 'config', 'database.js'));

fs
  .readdirSync(__dirname)
  .filter(file => {
    return (
      file.indexOf('.') !== 0 &&
      file !== basename &&
      file.slice(-3) === '.js' &&
      file.indexOf('.test.js') === -1
    );
  })
  .forEach(file => {
    const model = require(path.join(__dirname, file))(sequelize, Sequelize.DataTypes);
    db[model.name] = model;
  });

Object.keys(db).forEach(modelName => {
  if (db[modelName].associate) {
    db[modelName].associate(db);
  }
});

// Explicitly define associations
db.User.belongsTo(db.Company, { foreignKey: 'companyId', as: 'Company' });
db.Company.hasMany(db.User, { foreignKey: 'companyId' });

db.WorkdayEvent.belongsTo(db.User, { foreignKey: 'userId', as: 'User' });
db.User.hasMany(db.WorkdayEvent, { foreignKey: 'userId' });

db.WorkdayEvent.belongsTo(db.Company, { foreignKey: 'companyId' });
db.Company.hasMany(db.WorkdayEvent, { foreignKey: 'companyId' });

db.RefuelEvent.belongsTo(db.User, { foreignKey: 'userId', as: 'User' });
db.User.hasMany(db.RefuelEvent, { foreignKey: 'userId' });

db.RefuelEvent.belongsTo(db.Company, { foreignKey: 'companyId' });
db.Company.hasMany(db.RefuelEvent, { foreignKey: 'companyId' });

db.Settings.belongsTo(db.Company, { foreignKey: 'companyId' });
db.Company.hasOne(db.Settings, { foreignKey: 'companyId' });

db.sequelize = sequelize;
db.Sequelize = Sequelize;

module.exports = db;
