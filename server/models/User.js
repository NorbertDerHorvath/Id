const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');
const bcrypt = require('bcryptjs');

const User = sequelize.define('User', {
  id: {
    type: DataTypes.UUID,
    defaultValue: DataTypes.UUIDV4,
    primaryKey: true,
  },
  username: {
    type: DataTypes.STRING,
    allowNull: false,
    unique: true,
  },
  password: {
    type: DataTypes.STRING,
    allowNull: false,
  },
  role: {
    type: DataTypes.ENUM('superadmin', 'admin', 'user'),
    allowNull: false,
  },
  permissions: {
    type: DataTypes.JSON, // Using JSON type for permissions
    allowNull: true,
  },
  lastLoginTime: {
    type: DataTypes.DATE,
    allowNull: true,
  },
  lastLoginLocation: {
    type: DataTypes.STRING,
    allowNull: true,
  },
});

// Jelszó titkosítása mentés előtt
User.beforeCreate(async (user) => {
  const salt = await bcrypt.genSalt(10);
  user.password = await bcrypt.hash(user.password, salt);
});

// Jelszó ellenőrző metódus
User.prototype.isValidPassword = async function(password) {
  return await bcrypt.compare(password, this.password);
};

module.exports = User;
