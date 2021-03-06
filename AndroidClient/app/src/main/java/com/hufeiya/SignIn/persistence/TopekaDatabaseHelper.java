/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hufeiya.SignIn.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.hufeiya.SignIn.R;
import com.hufeiya.SignIn.model.Category;
import com.hufeiya.SignIn.model.JsonAttributes;
import com.hufeiya.SignIn.model.Theme;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Database for storing and retrieving info for categories and quizzes
 */
public class TopekaDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "TopekaDatabaseHelper";
    private static final String DB_NAME = "topeka";
    private static final String DB_SUFFIX = ".db";
    private static final int DB_VERSION = 1;
    private static List<Category> mCategories;
    private static TopekaDatabaseHelper mInstance;
    private final Resources mResources;

    private TopekaDatabaseHelper(Context context) {
        //prevents external instance creation
        super(context, DB_NAME + DB_SUFFIX, null, DB_VERSION);
        mResources = context.getResources();
    }

    private static TopekaDatabaseHelper getInstance(Context context) {
        if (null == mInstance) {
            mInstance = new TopekaDatabaseHelper(context);
        }
        return mInstance;
    }

    /**
     * Gets all categories with their quizzes.
     *
     * @param context      The context this is running in.
     * @param fromDatabase <code>true</code> if a data refresh is needed, else <code>false</code>.
     * @return All categories stored in the database.
     */
    public static List<Category> getCategories(Context context, boolean fromDatabase) {
        if (null == mCategories || fromDatabase) {
            mCategories = loadCategories(context);
        }
        return mCategories;
    }

    private static List<Category> loadCategories(Context context) {
        Cursor data = TopekaDatabaseHelper.getCategoryCursor(context);
        List<Category> tmpCategories = new ArrayList<>(data.getCount());
        final SQLiteDatabase readableDatabase = TopekaDatabaseHelper.getReadableDatabase(context);
        do {
            final Category category = getCategory(data, readableDatabase);
            tmpCategories.add(category);
        } while (data.moveToNext());
        return tmpCategories;
    }


    /**
     * Gets all categories wrapped in a {@link Cursor} positioned at it's first element.
     * <p>There are <b>no quizzes</b> within the categories obtained from this cursor</p>
     *
     * @param context The context this is running in.
     * @return All categories stored in the database.
     */
    private static Cursor getCategoryCursor(Context context) {
        SQLiteDatabase readableDatabase = getReadableDatabase(context);
        Cursor data = readableDatabase
                .query(CategoryTable.NAME, CategoryTable.PROJECTION, null, null, null, null, null);
        data.moveToFirst();
        return data;
    }

    /**
     * Gets a category from the given position of the cursor provided.
     *
     * @param cursor           The Cursor containing the data.
     * @param readableDatabase The database that contains the quizzes.
     * @return The found category.
     */
    private static Category getCategory(Cursor cursor, SQLiteDatabase readableDatabase) {
        // "magic numbers" based on CategoryTable#PROJECTION
        final String id = cursor.getString(0);
        final String name = cursor.getString(1);
        final String themeName = cursor.getString(2);
        final Theme theme = Theme.valueOf(themeName);

        return new Category(name, id, theme);
    }



    private static SQLiteDatabase getReadableDatabase(Context context) {
        return getInstance(context).getReadableDatabase();
    }

    private static SQLiteDatabase getWritableDatabase(Context context) {
        return getInstance(context).getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        /*
         * create the category table first, as quiz table has a foreign key
         * constraint on category id
         */
        db.execSQL(CategoryTable.CREATE);
        preFillDatabase(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /* no-op */
    }

    private void preFillDatabase(SQLiteDatabase db) {
        try {
            db.beginTransaction();
            try {
                fillCategoriesAndQuizzes(db);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "preFillDatabase", e);
        }
    }

    private void fillCategoriesAndQuizzes(SQLiteDatabase db) throws JSONException, IOException {
        ContentValues values = new ContentValues(); // reduce, reuse
        JSONArray jsonArray = new JSONArray(readCategoriesFromResources());
        JSONObject category;
        Log.d("category","json size is " + jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            category = jsonArray.getJSONObject(i);
            final String categoryId = category.getString(JsonAttributes.ID);
            Log.d("category",categoryId);
            fillCategory(db, values, category, categoryId);
        }
    }

    private String readCategoriesFromResources() throws IOException {
        StringBuilder categoriesJson = new StringBuilder();
        InputStream rawCategories = mResources.openRawResource(R.raw.categories);
        BufferedReader reader = new BufferedReader(new InputStreamReader(rawCategories));
        String line;

        while ((line = reader.readLine()) != null) {
            categoriesJson.append(line);
        }
        return categoriesJson.toString();
    }

    private void fillCategory(SQLiteDatabase db, ContentValues values, JSONObject category,
                              String categoryId) throws JSONException {
        values.clear();
        values.put(CategoryTable.COLUMN_ID, categoryId);
        values.put(CategoryTable.COLUMN_NAME, category.getString(JsonAttributes.NAME));
        values.put(CategoryTable.COLUMN_THEME, category.getString(JsonAttributes.THEME));
        values.put(CategoryTable.COLUMN_SOLVED, category.getString(JsonAttributes.SOLVED));
        db.insert(CategoryTable.NAME, null, values);
    }




}
