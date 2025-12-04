const { Sequelize } = require('sequelize');

// Adatbázis kapcsolat létrehozása a környezeti változóból
const sequelize = new Sequelize(process.env.DATABASE_URL, {
  dialect: 'postgres',
  protocol: 'postgres',
  dialectOptions: {
    ssl: {
      require: true,
      rejectUnauthorized: false // Render.com-hoz ez szükséges
    }
  }
});

module.exports = sequelize;
