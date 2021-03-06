package model;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Timer;
import java.util.TimerTask;

import commons.CommonLevel;
import commons.DBScore;
import commons.Direction;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.data.levels.SokobanLevel;
import model.data.loaders.LevelLoader;
import model.data.loaders.LevelLoaderFactory;
import model.data.policies.SokobanPolicy;
import model.data.savers.LevelSaver;
import model.data.savers.LevelSaverFactory;
import model.data.utils.CellsToSymbolsConverter;
import model.db.DBLevel;
import model.db.DBUser;
import model.db.ScoresDBManager;

public class SokobanModel extends Observable implements Model {

	private SokobanLevel level;
	private SokobanPolicy policy;
	private Timer timer;
	private boolean timeRunning = false;
	private boolean declaredSolved = false;

	private StringProperty stepsTakenProperty;
	private StringProperty elapsedTimeProperty;
	private StringProperty levelNameProperty;

	private ScoresDBManager dbManager;

	public SokobanModel() {
		policy = new SokobanPolicy();

		stepsTakenProperty = new SimpleStringProperty();
		elapsedTimeProperty = new SimpleStringProperty();
		levelNameProperty = new SimpleStringProperty();

	}

	@Override
	public void loadLevel(String filePath) {
		stopTimer();

		String extension = parseExtension(filePath);

		LevelLoaderFactory loaderFactory = new LevelLoaderFactory();

		LevelLoader loader = loaderFactory.CreateLoader(extension);

		try {

			level = loader.loadLevel(new FileInputStream(filePath));

			policy.setLevel(level);
			updateStringProperties();
			declaredSolved = false;
			LinkedList<String> displayParams = new LinkedList<>();
			displayParams.add("DISPLAY");
			setChanged();
			notifyObservers(displayParams);
		} catch (Exception e) {
			LinkedList<String> displayParams = new LinkedList<>();
			displayParams.add("DISPLAYMESSAGE");
			displayParams.add("Error loading file");
			setChanged();
			notifyObservers(displayParams);
		}

	}

	private void updateStringProperties() {

		stepsTakenProperty.set(level.getStepsTaken().toString());
		elapsedTimeProperty.set(level.getElapsedTime().toString());
		levelNameProperty.set(level.getName());

	}

	@Override
	public void saveLevel(String filePath) {
		String extension = parseExtension(filePath);

		LevelSaverFactory saverFactory = new LevelSaverFactory();

		LevelSaver saver = saverFactory.CreateSaver(extension);

		try {
			saver.saveLevel(level, new FileOutputStream(filePath));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private String parseExtension(String filePath) {

		int dotIndex = filePath.lastIndexOf('.');
		String extension = filePath.substring(dotIndex + 1);

		return extension;

	}

	private boolean isValidDirection(String direction) {

		for (Direction validDirection : Direction.values()) {

			if (direction.equals(validDirection.name()))
				return true;

		}

		return false;

	}

	@Override
	public void moveHero(int heroIndex, String direction) {
		if (level == null)
			return;

		if (!isValidDirection(direction.toUpperCase()))
			return;

		Direction dir = Direction.valueOf(direction.toUpperCase());

		if (policy.isLegal(heroIndex, dir)) {

			level.moveHero(heroIndex, dir);
			int stepsTaken = level.getStepsTaken();
			level.setStepsTaken(stepsTaken + 1);
			stepsTakenProperty.set(level.getStepsTaken().toString());
		}

		if (!timeRunning && !declaredSolved)
			startTimer();

		if (level.isSolved() && !declaredSolved) {
			stopTimer();
			LinkedList<String> displayParams = new LinkedList<>();
			displayParams.add("DECLARESOLVED");
			setChanged();
			notifyObservers(displayParams);
			declaredSolved = true;

		}

		LinkedList<String> displayParams = new LinkedList<>();
		displayParams.add("DISPLAY");
		setChanged();
		notifyObservers(displayParams);
	}

	private void startTimer() {

		timer = new Timer();

		timer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {

				level.setElapsedTime(level.getElapsedTime() + 1);
				elapsedTimeProperty.set(level.getElapsedTime().toString());

			};

		}, 0, 1000);

		timeRunning = true;
	}

	private void stopTimer() {
		if (timer != null) {
			timer.cancel();
			timeRunning = false;
		}

	}

	@Override
	public int getElapsedTime() {
		return level.getElapsedTime();
	}

	@Override
	public int getStepsTaken() {
		return level.getStepsTaken();
	}

	@Override
	public CommonLevel getCommonLevel() {

		String name = level.getName();
		int stepsTaken = level.getStepsTaken();
		int elapsedTime = level.getElapsedTime();
		ArrayList<ArrayList<Character>> map;

		CellsToSymbolsConverter converter = new CellsToSymbolsConverter();

		map = converter.convert(level.getMap());

		return new CommonLevel(name, stepsTaken, elapsedTime, map);
	}

	@Override
	public StringProperty getStepsTakenProperty() {
		return stepsTakenProperty;
	}

	@Override
	public StringProperty getElapsedTimeProperty() {
		return elapsedTimeProperty;
	}

	@Override
	public StringProperty getLevelNamePropertyProperty() {
		return levelNameProperty;
	}

	@Override
	public void saveScoreToDB(String userName) {

		if (dbManager == null)
			dbManager = new ScoresDBManager();

		DBUser userToSave = new DBUser(userName);
		dbManager.addUser(userToSave);

		DBLevel levelToSave = new DBLevel(level.getName());

		dbManager.addLevel(levelToSave);

		dbManager.addScore(userToSave, levelToSave, level.getStepsTaken(), level.getElapsedTime());

	}

	@Override
	public void stop() {
		stopTimer();

		if (dbManager != null)
			dbManager.getFactory().close();

	}

	@Override
	public List<DBScore> getLeaderboard(String userName, String levelName, String orderBy, int firstIndex,int maxResults) {
		if (dbManager == null)
			dbManager = new ScoresDBManager();

		if (this.level != null && levelName.equals("CurrentLevel"))
			levelName = level.getName();

		else if (levelName.isEmpty())
			levelName = null;

		List<DBScore> leaderboard = dbManager.getScores(levelName, userName, orderBy, firstIndex, maxResults);

		return leaderboard;
	}

}
