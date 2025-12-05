const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const WorkdayEvent = sequelize.define('WorkdayEvent', {
  id: {
    type: DataTypes.BIGINT,
    primaryKey: true,
    autoIncrement: true, // Matching Android's Long
  },
  role: {
    type: DataTypes.STRING,
    allowNull: false,
  },
  startTime: {
    type: DataTypes.DATE,
    allowNull: true,
  },
  endTime: {
    type: DataTypes.DATE,
  },
  startLocation: {
    type: DataTypes.STRING,
  },
  endLocation: {
    type: DataTypes.STRING,
  },
  startOdometer: {
    type: DataTypes.INTEGER,
  },
  endOdometer: {
    type: DataTypes.INTEGER,
  },
  carPlate: {
    type: DataTypes.STRING,
  },
  type: {
    type: DataTypes.STRING,
    defaultValue: 'WORK',
  },
});

module.exports = WorkdayEvent;
