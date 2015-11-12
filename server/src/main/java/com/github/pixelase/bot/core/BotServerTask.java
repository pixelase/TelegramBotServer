package com.github.pixelase.bot.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.github.pixelase.bot.api.ModuleTask;
import com.github.pixelase.bot.api.Server;
import com.github.pixelase.bot.api.Task;
import com.github.pixelase.bot.api.UserTask;
import com.pengrad.telegrambot.TelegramBotAdapter;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.response.GetUpdatesResponse;

public class BotServerTask extends Task implements Server {
	private Properties properties;
	private ExecutorService moduleExecutor;
	private boolean isStarted;
	private ModuleTask[] modules;
	private static long updatesFetchDelay;

	private BotServerTask() {
		properties = new Properties();
		moduleExecutor = Executors.newCachedThreadPool();
		isStarted = false;
	}

	public BotServerTask(String propFilePath, ModuleTask... modules) throws IOException {
		this();
		this.modules = modules;
		configure(propFilePath);
	}

	@Override
	public void configure(String propFilePath) throws IOException {
		File propFile = new File(propFilePath);

		/*
		 * Reading properties
		 */
		if (propFile.exists()) {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(propFile));
			properties.load(bis);
			bot = TelegramBotAdapter.build(properties.getProperty("token"));
			BotServerTask.setUpdatesFetchDelay(Long.parseLong(properties.getProperty("updatesFetchDelay")));
			Task.setTaskDelay(Long.parseLong(properties.getProperty("taskDelay")));
			Task.setTaskDelay(Long.parseLong(properties.getProperty("taskDelay")));
			ModuleTask.setModuleTaskDelay(Long.parseLong(properties.getProperty("moduleTaskDelay")));
			UserTask.setUserTaskDelay(Long.parseLong(properties.getProperty("userTaskDelay")));
			UserTask.setUserTaskTimeout(Long.parseLong(properties.getProperty("userTaskTimeout")));
			bis.close();
		} else {
			throw new FileNotFoundException("The properties file is not found");
		}
	}

	private void fetchUpdates() throws InterruptedException {
		int offset = 0;
		final int limit = 1;
		GetUpdatesResponse getUpdatesResponse = null;
		Update currentUpdate = null;

		do {
			/*
			 * Some delay in fetching updates
			 */
			Thread.sleep(updatesFetchDelay);

			/*
			 * Receiving the latest updates and mark all previous updates as
			 * handled.
			 */
			getUpdatesResponse = bot.getUpdates(offset, limit, 0);

			/*
			 * Skip iteration if we don't have updates
			 */
			if (getUpdatesResponse.updates().isEmpty()) {
				continue;
			}

			/*
			 * Get unique update and it's offset that used to mark all previous
			 * updates as handled.
			 */
			currentUpdate = getUpdatesResponse.updates().get(limit - 1);
			offset = (getUpdatesResponse.updates().size() == limit) ? currentUpdate.updateId() + 1 : 0;

			/*
			 * Update state for each task
			 */
			currentMessage = currentUpdate.message();
			isOk = getUpdatesResponse.isOk();

			/*
			 * For debug
			 */
			System.out
					.println("Message from " + this.getClass().getSimpleName() + " " + currentUpdate.message().text());
		} while (getUpdatesResponse.isOk());
	}

	@Override
	public void start() throws InterruptedException {
		if (isStarted) {
			System.out.println("The server is already started");
		}

		if (modules.length != 0) {
			for (ModuleTask module : modules) {
				moduleExecutor.submit(module);
			}
		}
		isStarted = true;
		fetchUpdates();
	}

	@Override
	public void stop() {
		if (!isStarted) {
			System.out.println("The server hasn't been started");
		}
		moduleExecutor.shutdown();
		isStarted = false;
	}

	@Override
	public void refresh() throws InterruptedException {
		isStarted = false;
		start();
	}

	public static long getUpdatesFetchDelay() {
		return updatesFetchDelay;
	}

	public static void setUpdatesFetchDelay(long updatesFetchDelay) {
		BotServerTask.updatesFetchDelay = updatesFetchDelay;
	}

	@Override
	public void run() {
		// TODO for multithreading
	}
}