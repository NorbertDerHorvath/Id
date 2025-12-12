module.exports = (sequelize, DataTypes) => {
  const WorkdayEvent = sequelize.define('WorkdayEvent', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'Users',
        key: 'id',
      },
    },
    companyId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'Companies',
        key: 'id',
      },
    },
    eventType: {
      type: DataTypes.ENUM('workday', 'vacation', 'sick_leave', 'paid_holiday'),
      defaultValue: 'workday',
      allowNull: false,
    },
    startTime: {
      type: DataTypes.DATE,
      allowNull: false,
    },
    endTime: {
      type: DataTypes.DATE,
      allowNull: true,
    },
    breakTime: {
      type: DataTypes.INTEGER, // in minutes
      allowNull: true,
    },
    startOdometer: {
      type: DataTypes.INTEGER,
      allowNull: true,
    },
    endOdometer: {
      type: DataTypes.INTEGER,
      allowNull: true,
    },
    carPlate: {
      type: DataTypes.STRING,
      allowNull: true,
    },
    startLocation: {
      type: DataTypes.STRING,
      allowNull: true,
    },
    endLocation: {
      type: DataTypes.STRING,
      allowNull: true,
    },
    comment: {
      type: DataTypes.TEXT,
      allowNull: true,
    },
  });

  return WorkdayEvent;
};