module.exports = (sequelize, DataTypes) => {
  const LoadingEvent = sequelize.define('LoadingEvent', {
    id: {
      type: DataTypes.BIGINT,
      primaryKey: true,
      autoIncrement: true,
    },
    startTime: {
      type: DataTypes.DATE,
      allowNull: false,
    },
    endTime: {
      type: DataTypes.DATE,
    },
    location: {
      type: DataTypes.STRING,
    },
  });

  return LoadingEvent;
};