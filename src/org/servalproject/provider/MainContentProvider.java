/*
 * Copyright (C) 2012 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.servalproject.provider;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

/**
 * main content provider for the Serval Mesh software
 */
public class MainContentProvider extends ContentProvider {

	/*
	 * public class level constants
	 */
	/**
	 * authority string for the content provider
	 */
	public static final String AUTHORITY = "org.servalproject.provider";

	/*
	 * private class level constants
	 */
	private final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

	private final int THREADS_LIST_URI = 0;
	private final int THREADS_ITEM_URI = 1;

	private final int MESSAGES_LIST_URI = 10;
	private final int MESSAGES_ITEM_URI = 11;
	private final int MESSAGES_GROUPED_LIST_URI = 12;

	/*
	 * private class level variables
	 */
	private MainDatabaseHelper databaseHelper = null;
	private SQLiteDatabase database;

	private final String TAG = "MainContentProvider";
	private final boolean V_LOG = true;

	@Override
	public boolean onCreate() {

		return openDatabase();
	}

	private boolean openDatabase() {

		// get the path for the directory
		if (ServalBatPhoneApplication.context != null) {
			String mPath = Environment.getExternalStorageDirectory().getPath();
			mPath += ServalBatPhoneApplication.context
					.getString(R.string.system_sqlite_database_path);

			databaseHelper = new MainDatabaseHelper(getContext(), mPath);

			URI_MATCHER.addURI(AUTHORITY, ThreadsContract.CONTENT_URI_PATH,
					THREADS_LIST_URI);

			URI_MATCHER.addURI(AUTHORITY, ThreadsContract.CONTENT_URI_PATH
					+ "/#",
					THREADS_ITEM_URI);

			URI_MATCHER.addURI(AUTHORITY, MessagesContract.CONTENT_URI_PATH,
					MESSAGES_LIST_URI);

			URI_MATCHER.addURI(AUTHORITY, MessagesContract.CONTENT_URI_PATH
					+ "/#",
					MESSAGES_ITEM_URI);

			URI_MATCHER.addURI(AUTHORITY, MessagesContract.CONTENT_URI_PATH
					+ "/grouped-list",
					MESSAGES_GROUPED_LIST_URI);

			if (V_LOG) {
				Log.v(TAG, "content provider created");
			}

			return true;
		} else {
			return false;
		}

	}

	@Override
	public String getType(Uri uri) {

		if (databaseHelper == null) {
			openDatabase();
		}

		// determine the mime type to return
		switch (URI_MATCHER.match(uri)) {
		case THREADS_LIST_URI:
			// uri matches all of the table
			return ThreadsContract.CONTENT_TYPE_LIST;
		case THREADS_ITEM_URI:
			return ThreadsContract.CONTENT_TYPE_ITEM;
		case MESSAGES_LIST_URI:
			// uri matches all of the table
			return MessagesContract.CONTENT_TYPE_LIST;
		case MESSAGES_ITEM_URI:
			// uri matches an individual item
			return MessagesContract.CONTENT_TYPE_ITEM;
		case MESSAGES_GROUPED_LIST_URI:
			return MessagesContract.CONTENT_TYPE_LIST;
		default:
			// unknown uri found
			Log.e(TAG, "unknown URI detected on get type: " + uri.toString());
			return null;
		}
	}

	@Override
	public synchronized Cursor query(Uri uri, String[] projection,
			String selection, String[] selectionArgs, String sortOrder) {

		if (databaseHelper == null) {
			openDatabase();
		}

		Cursor mResults = null;
		String mTableName = null;

		// choose the table name and sort order based on the URI
		switch (URI_MATCHER.match(uri)) {
		case THREADS_LIST_URI:
			// uri matches all of the table
			if (TextUtils.isEmpty(sortOrder) == true) {
				sortOrder = ThreadsContract.Table._ID + " ASC";
			}
			mTableName = ThreadsContract.Table.TABLE_NAME;
			break;
		case THREADS_ITEM_URI:
			// uri matches an individual item
			if (TextUtils.isEmpty(selection) == true) {
				selection = ThreadsContract.Table._ID + " = "
						+ uri.getLastPathSegment();
			} else {
				selection += "AND " + ThreadsContract.Table._ID + " = "
						+ uri.getLastPathSegment();
			}
			mTableName = ThreadsContract.Table.TABLE_NAME;
			break;
		case MESSAGES_LIST_URI:
			// uri matches all of the table
			if (TextUtils.isEmpty(sortOrder) == true) {
				sortOrder = MessagesContract.Table._ID + " ASC";
			}
			mTableName = MessagesContract.Table.TABLE_NAME;
			break;
		case MESSAGES_ITEM_URI:
			// uri matches an individual item
			if (TextUtils.isEmpty(selection) == true) {
				selection = MessagesContract.Table._ID + " = "
						+ uri.getLastPathSegment();
			} else {
				selection += "AND " + MessagesContract.Table._ID + " = "
						+ uri.getLastPathSegment();
			}
			mTableName = MessagesContract.Table.TABLE_NAME;
			break;
		case MESSAGES_GROUPED_LIST_URI:
			return getGroupedMessagesList();
		default:
			// unknown uri found
			Log.e(TAG, "unknown URI detected on query: " + uri.toString());
			return null;
		}

		// get a connection to the database
		database = databaseHelper.getReadableDatabase();

		// actually run the query
		mResults = database.query(
				mTableName,
				projection,
				selection,
				selectionArgs,
				null,
				null,
				sortOrder);

		return mResults;
	}

	private Cursor getGroupedMessagesList() {

		// define the projection
		String[] mColumns = new String[4];
		mColumns[0] = MessagesContract.Table._ID;
		mColumns[1] = MessagesContract.Table.RECIPIENT_PHONE;
		mColumns[2] = "MAX( " + MessagesContract.Table.RECEIVED_TIME
				+ ") AS MAX_RECEIVED_TIME";
		mColumns[3] = "COUNT( " + MessagesContract.Table.RECIPIENT_PHONE
				+ ") AS COUNT_RECIPIENT_PHONE";

		// get a connection to the database
		database = databaseHelper.getReadableDatabase();

		return database.query(
				MessagesContract.Table.TABLE_NAME,
				mColumns,
				null,
				null,
				MessagesContract.Table.RECIPIENT_PHONE,
				null,
				MessagesContract.Table.RECIPIENT_PHONE);
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {

		if (databaseHelper == null) {
			openDatabase();
		}

		Uri mResults = null;
		String mTable = null;
		Uri mContentUri = null;

		// chose the table name
		switch (URI_MATCHER.match(uri)) {
		case THREADS_LIST_URI:
			// uri matches all of the table
			mTable = ThreadsContract.CONTENT_URI_PATH;
			mContentUri = ThreadsContract.CONTENT_URI;
			break;
		case MESSAGES_LIST_URI:
			// uri matches all of the table
			mTable = MessagesContract.CONTENT_URI_PATH;
			mContentUri = MessagesContract.CONTENT_URI;
			break;
		default:
			// unknown uri found
			Log.e(TAG, "unknown URI detected on query: " + uri.toString());
			return null;
		}

		// get a connection to the database
		database = databaseHelper.getWritableDatabase();

		long mId = database.insertOrThrow(mTable, null, values);

		// play nice and tidy up
		database.close();

		mResults = ContentUris.withAppendedId(mContentUri, mId);
		getContext().getContentResolver().notifyChange(mResults, null);

		return mResults;
	}

	@Override
	public synchronized int delete(Uri arg0, String arg1, String[] arg2) {
		// TODO implement code when required
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public synchronized int update(Uri arg0, ContentValues arg1, String arg2,
			String[] arg3) {
		// TODO implement code when required
		throw new UnsupportedOperationException("Not implemented yet");
	}

}