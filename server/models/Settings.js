const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Settings = sequelize.define('Settings', {
  id: {
    type: DataTypes.UUID,
    defaultValue: DataTypes.UUIDV4,
    primaryKey: true,
  },
  settings: {
    type: DataTypes.JSON,
    allowNull: false,
    defaultValue: {},
  },
  // Foreign Keys
  companyId: {
    type: DataTypes.UUID,
    allowNull: true, // Can be null for global or user-specific settings
    references: {
      model: 'Companies',
      key: 'id',
    },
  },
  userId: {
    type: DataTypes.UUID,
    allowNull: true, // Can be null for global or company-wide settings
    references: {
      model: 'Users',
      key: 'id',
    },
  },
});

module.exports = Settings;
