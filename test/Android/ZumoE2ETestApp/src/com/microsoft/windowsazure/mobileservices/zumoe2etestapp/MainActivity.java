/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Apache 2.0 License
 
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
     http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 
See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */
package com.microsoft.windowsazure.mobileservices.zumoe2etestapp;

import java.net.MalformedURLException;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.TestCase;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.TestExecutionCallback;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.TestGroup;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.TestResult;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.tests.LoginTests;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.tests.MiscTests;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.tests.QueryTests;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.tests.RoundTripTests;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.tests.UpdateDeleteTests;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {

	private StringBuilder mLog;

	private SharedPreferences mPrefManager;

	private ListView mTestCaseList;
	private Spinner mTestGroupSpinner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mPrefManager = PreferenceManager.getDefaultSharedPreferences(this);

		mTestCaseList = (ListView) findViewById(R.id.testCaseList);
		TestCaseAdapter testCaseAdapter = new TestCaseAdapter(this, R.layout.row_list_test_case);
		mTestCaseList.setAdapter(testCaseAdapter);

		mTestGroupSpinner = (Spinner) findViewById(R.id.testGroupSpinner);

		ArrayAdapter<TestGroup> testGroupAdapter = new ArrayAdapter<TestGroup>(this, android.R.layout.simple_spinner_item);
		mTestGroupSpinner.setAdapter(testGroupAdapter);
		mTestGroupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				selectTestGroup(pos);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// do nothing
			}
		});

		refreshTestGroupsAndLog();
	}

	private void selectTestGroup(int pos) {
		TestGroup tg = (TestGroup) mTestGroupSpinner.getItemAtPosition(pos);
		List<TestCase> testCases = tg.getTestCases();

		fillTestList(testCases);
	}

	@SuppressWarnings("unchecked")
	private void refreshTestGroupsAndLog() {
		mLog = new StringBuilder();

		ArrayAdapter<TestGroup> adapter = (ArrayAdapter<TestGroup>) mTestGroupSpinner.getAdapter();
		adapter.clear();
		adapter.add(new RoundTripTests());
		adapter.add(new MiscTests());
		adapter.add(new LoginTests());
		adapter.add(new UpdateDeleteTests());
		adapter.add(new QueryTests());
		mTestGroupSpinner.setSelection(0);
		selectTestGroup(0);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			startActivity(new Intent(this, ZumoPreferenceActivity.class));
			return true;

		case R.id.menu_run_tests:
			if (getMobileServiceKey().trim() == "" || getMobileServiceURL().trim() == "") {
				startActivity(new Intent(this, ZumoPreferenceActivity.class));
			} else {
				runTests();
			}
			return true;

		case R.id.menu_check_all:
			changeCheckAllTests(true);
			return true;

		case R.id.menu_uncheck_all:
			changeCheckAllTests(false);
			return true;

		case R.id.menu_reset:
			refreshTestGroupsAndLog();
			return true;

		case R.id.menu_view_log:
			AlertDialog.Builder logDialogBuilder = new AlertDialog.Builder(this);
			logDialogBuilder.setTitle("Log");

			final EditText editText = new EditText(this);
			editText.setText(mLog.toString());
			editText.setKeyListener(null);

			logDialogBuilder.setPositiveButton("Copy", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
					clipboardManager.setText(editText.getText());
				}
			});

			logDialogBuilder.setView(editText);

			logDialogBuilder.create().show();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void changeCheckAllTests(boolean check) {
		TestGroup tg = (TestGroup) mTestGroupSpinner.getSelectedItem();
		List<TestCase> testCases = tg.getTestCases();

		for (TestCase testCase : testCases) {
			testCase.setEnabled(check);
		}

		fillTestList(testCases);
	}

	private void fillTestList(List<TestCase> testCases) {
		TestCaseAdapter testCaseAdapter = (TestCaseAdapter) mTestCaseList.getAdapter();

		testCaseAdapter.clear();
		for (TestCase testCase : testCases) {
			testCaseAdapter.add(testCase);
		}
	}

	private void runTests() {
		MobileServiceClient client;

		try {
			client = createMobileServiceClient();
		} catch (MalformedURLException e) {
			createAndShowDialog(e, "Error");
			return;
		}

		TestGroup group = (TestGroup) mTestGroupSpinner.getSelectedItem();

		group.runTests(client, new TestExecutionCallback() {

			@Override
			public void onTestStart(TestCase test) {
				TestCaseAdapter adapter = (TestCaseAdapter) mTestCaseList.getAdapter();
				adapter.notifyDataSetChanged();
				log("TEST START", test.getName());
			}

			@Override
			public void onTestGroupComplete(TestGroup group, List<TestResult> results) {
				log("TEST GROUP COMPLETED", group.getName() + " - " + group.getStatus().toString());
			}

			@Override
			public void onTestComplete(TestCase test, TestResult result) {
				Throwable e = result.getException();
				String exMessage = "-";
				if (e != null) {
					StringBuilder sb = new StringBuilder();
					while (e != null) {
						sb.append(e.getMessage());
						sb.append(" // ");
						e = e.getCause();
					}

					exMessage = sb.toString();
				}

				TestCaseAdapter adapter = (TestCaseAdapter) mTestCaseList.getAdapter();
				adapter.notifyDataSetChanged();
				log("TEST LOG", test.getLog());
				log("TEST COMPLETED", test.getName() + " - " + result.getStatus().toString() + " - Ex: " + exMessage);
			}
		});

	}

	@SuppressWarnings("unused")
	private void log(String content) {
		log("Info", content);
	}

	private void log(String title, String content) {
		String message = title + " - " + content;
		Log.d("ZUMO-E2ETESTAPP", message);

		mLog.append(message);
		mLog.append('\n');
	}

	private String getMobileServiceURL() {
		return mPrefManager.getString(Constants.PREFERENCE_MOBILE_SERVICE_URL, "");
	}

	private String getMobileServiceKey() {
		return mPrefManager.getString(Constants.PREFERENCE_MOBILE_SERVICE_KEY, "");
	}

	private MobileServiceClient createMobileServiceClient() throws MalformedURLException {
		String url = getMobileServiceURL();
		String key = getMobileServiceKey();

		MobileServiceClient client = new MobileServiceClient(url, key, this);

		return client;
	}

	/**
	 * Creates a dialog and shows it
	 * 
	 * @param exception
	 *            The exception to show in the dialog
	 * @param title
	 *            The dialog title
	 */
	private void createAndShowDialog(Exception exception, String title) {
		createAndShowDialog(exception.toString(), title);
	}

	/**
	 * Creates a dialog and shows it
	 * 
	 * @param message
	 *            The dialog message
	 * @param title
	 *            The dialog title
	 */
	private void createAndShowDialog(String message, String title) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setMessage(message);
		builder.setTitle(title);
		builder.create().show();
	}

}
