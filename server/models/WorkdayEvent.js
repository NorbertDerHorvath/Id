module.exports = (sequelize, DataTypes) => {
  const WorkdayEvent = sequelize.define('WorkdayEvent', {
    id: {
      type: DataTypes.BIGINT,
      primaryKey: true,
      autoIncrement: true,
    },
    startTime: {
      type: DataTypes.DATE,
      allowNull: true,
    },
    endTime: {
      type: DataTypes.DATE,
    },
    breakTime: {
      type: DataTypes.INTEGER,
      defaultValue: 0,
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

  return WorkdayEvent;
};