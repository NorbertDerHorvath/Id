const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const RefuelEvent = sequelize.define('RefuelEvent', {
  id: {
    type: DataTypes.BIGINT,
    primaryKey: true,
    autoIncrement: true,
  },
  timestamp: {
    type: DataTypes.DATE,
    allowNull: false,
  },
  odometer: {
    type: DataTypes.INTEGER,
    allowNull: false,
  },
  fuelType: {
    type: DataTypes.STRING,
  },
  fuelAmount: {
    type: DataTypes.DOUBLE,
  },
  paymentMethod: {
    type: DataTypes.STRING,
  },
  carPlate: {
    type: DataTypes.STRING,
  },
  location: {
    type: DataTypes.STRING,
  },
  value: {
    type: DataTypes.DOUBLE,
  },
});

module.exports = RefuelEvent;
