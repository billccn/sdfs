package org.opendedup.sdfs.filestore;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

import org.opendedup.logging.SDFSLogger;
import org.opendedup.sdfs.Main;
import org.opendedup.sdfs.io.DedupFile;

/**
 * 
 * @author Sam Silverberg
 * 
 *         This class initiates a thread that is used to monitor open files
 *         within the DedupFileStore. It will close them if left open and
 *         untouched for longer than the @see
 *         com.annesam.sdfs.Main#maxInactiveFileTime .
 * 
 */
public class OpenFileMonitor implements Runnable {

	int interval = 5000;
	int maxInactive = 900000;
	boolean closed = false;
	boolean done = false;
	Thread th = null;

	/**
	 * Instantiates the OpenFileMonitor
	 * 
	 * @param interval
	 *            the interval to check for inactive files
	 * @param maxInactive
	 *            the maximum time allowed for a file to be inactive. Inactivity
	 *            is determined by last accessed time.
	 */
	public OpenFileMonitor(int interval, int maxInactive) {
		this.interval = interval;
		this.maxInactive = maxInactive;
		th = new Thread(this);
		th.start();
	}

	@Override
	public void run() {
		try {
			while (!closed) {
				try {
					Thread.sleep(this.interval);
				} catch (InterruptedException e) {
					if (this.closed)
						break;
				}
				try {
					DedupFile[] files = DedupFileStore.getArray();
					for (int i = 0; i < files.length; i++) {
						DedupFile df = null;
						try {
							df = files[i];
							if (!Main.safeClose && this.isFileStale(df)
									&& !df.hasOpenChannels()) {
								try {
									if (df != null) {
										DedupFileStore.getDedupFile(
												df.getMetaFile()).forceClose();
										if (SDFSLogger.isDebug())
											SDFSLogger
													.getLog()
													.debug("Closing ["
															+ df.getMetaFile()
																	.getPath()
															+ "] because its stale");
									}
								} catch (Exception e) {
									SDFSLogger.getLog().warn(
											"Unable close file for "
													+ df.getMetaFile()
															.getPath(), e);
								}
							} else if (!Main.safeSync) {
								df.sync(true);
							}
						} catch (NoSuchFileException e) {
							try {
								SDFSLogger.getLog().warn(
										"OpenFile Monitor could not find file "
												+ df.getMetaFile().getPath());
								// df.forceClose();
							} catch (Exception e1) {

							}
						}
					}
				} catch (NoSuchFileException e) {

				} catch (Exception e) {
					SDFSLogger.getLog().warn("Unable check files", e);
				}
			}
		} finally {
			done = true;
		}
	}

	/**
	 * Checks if a file should be closed
	 * 
	 * @param df
	 *            the DedupFile to check
	 * @return true if stale.
	 * @throws IOException
	 */
	public boolean isFileStale(DedupFile df) throws IOException {
		if (this.maxInactive == -1)
			return false;
		else {
			long currentTime = System.currentTimeMillis();
			long staleTime = 0;
			try {
				if(df.getMetaFile() == null)
					return true;
				else
					staleTime = df.getMetaFile().getLastAccessed()
						+ this.maxInactive;
			} catch (Exception e) {
				SDFSLogger.getLog().error("error checking last accessed", e);
			}
			return currentTime > staleTime;
		}
	}

	/**
	 * Closes the OpenFileMonitor thread.
	 * 
	 * @throws InterruptedException
	 */
	public void close() {
		this.closed = true;
		th.interrupt();
		while (!done) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				break;
			}
		}
	}

}
