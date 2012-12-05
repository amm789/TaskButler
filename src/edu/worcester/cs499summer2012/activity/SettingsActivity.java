/* 
 * SettingsActivity.java
 * 
 * Copyright 2012 Jonathan Hasenzahl, James Celona, Dhimitraq Jorgji
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package edu.worcester.cs499summer2012.activity;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.format.DateFormat;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;

import edu.worcester.cs499summer2012.R;
import edu.worcester.cs499summer2012.adapter.TaskListAdapter;
import edu.worcester.cs499summer2012.database.TasksDataSource;
import edu.worcester.cs499summer2012.service.TaskAlarm;
import edu.worcester.cs499summer2012.service.TaskButlerService;
import edu.worcester.cs499summer2012.service.TaskButlerWidgetProvider;
import edu.worcester.cs499summer2012.service.WakefulIntentService;
import edu.worcester.cs499summer2012.task.BackupManager;
import edu.worcester.cs499summer2012.task.Task;
import edu.worcester.cs499summer2012.task.ToastMaker;

public class SettingsActivity extends SherlockPreferenceActivity implements 
	OnPreferenceClickListener, OnPreferenceChangeListener {

	public static final String AUTO_SORT = "auto_sort";
	public static final String CUSTOM_SORT = "custom_sort";
	public static final String HIDE_COMPLETED = "hide_completed";
	public static final String DEFAULT_HOUR = "default_hour";
	public static final String VIBRATE_ON_ALARM = "vibrate_on_alarm";
	public static final String REMINDER_TIME = "reminder_time";
	public static final String ALARM_TIME = "alarm_time";
	public static final String BACKUP = "backup";
	public static final String LAST_BACKUP = "last_backup";
	public static final String RESTORE = "restore";
	public static final String SORT_TYPE = "sort_type";
	public static final String DISPLAY_CATEGORY = "display_category";
	
	public static final String DEFAULT_REMINDER_TIME = "6";
	public static final String DEFAULT_ALARM_TIME = "15";
	public static final String DEFAULT_HOUR_VALUE = "12";
	public static final long DEFAULT_LAST_BACKUP = 0;
	
	private TasksDataSource data_source;
	private SharedPreferences prefs;
	private SharedPreferences.Editor prefs_editor;
	private Activity activity;
	private Context context;
	
	private CheckBoxPreference cbp_auto_sort;
	private CheckBoxPreference cbp_custom_sort;
	private CheckBoxPreference cpb_vibrate;
	private ListPreference lp_reminder_time;
	private ListPreference lp_alarm_time;
	private ListPreference lp_default_hour;
	private PreferenceScreen ps_backup;
	private PreferenceScreen ps_restore;
	
    @SuppressWarnings("deprecation")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.preferences);
        activity = this;
        context = this;
        
        // Allow Action bar icon to act as a button
        ActionBar action_bar = getSupportActionBar();
        action_bar.setIcon(R.drawable.ic_settings);
        action_bar.setHomeButtonEnabled(true);
        action_bar.setDisplayHomeAsUpEnabled(true);
        
		// Open the database
		data_source = TasksDataSource.getInstance(this);

		// Read preferences from file
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs_editor = prefs.edit();
        
		// Initialize preferences objects
        cbp_auto_sort = (CheckBoxPreference) this.findPreference(AUTO_SORT);
        cbp_custom_sort = (CheckBoxPreference) this.findPreference(CUSTOM_SORT);
        cpb_vibrate = (CheckBoxPreference) this.findPreference(VIBRATE_ON_ALARM);
        lp_reminder_time = (ListPreference) this.findPreference(REMINDER_TIME);
        lp_alarm_time = (ListPreference) this.findPreference(ALARM_TIME);
        lp_default_hour = (ListPreference) this.findPreference(DEFAULT_HOUR);
        ps_backup = (PreferenceScreen) this.findPreference(BACKUP);
        ps_restore = (PreferenceScreen) this.findPreference(RESTORE);
        
        // Set listeners
        cbp_auto_sort.setOnPreferenceClickListener(this);
        cbp_custom_sort.setOnPreferenceClickListener(this);
        ps_backup.setOnPreferenceClickListener(this);
        ps_restore.setOnPreferenceClickListener(this);
        lp_reminder_time.setOnPreferenceChangeListener(this);
        lp_alarm_time.setOnPreferenceChangeListener(this);
        lp_default_hour.setOnPreferenceChangeListener(this);
        cpb_vibrate.setOnPreferenceChangeListener(this);
        
        // Set checkbox states
        if (prefs.getInt(SORT_TYPE, TaskListAdapter.AUTO_SORT) == TaskListAdapter.AUTO_SORT) {
        	cbp_auto_sort.setChecked(true);
        	cbp_custom_sort.setChecked(false);
        } else {
        	cbp_auto_sort.setChecked(false);
        	cbp_custom_sort.setChecked(true);
        }
        
        // Set ListPreferences
        lp_reminder_time.setSummary(getReminderSummary(REMINDER_TIME, 
        		prefs.getString(REMINDER_TIME, DEFAULT_REMINDER_TIME)));
        lp_alarm_time.setSummary(getReminderSummary(ALARM_TIME, 
        		prefs.getString(ALARM_TIME, DEFAULT_ALARM_TIME)));
        lp_default_hour.setSummary(getHourSummary(prefs.getString(DEFAULT_HOUR, DEFAULT_HOUR_VALUE)));
        
        // Set last backup
        setBackupSummary();
    }
    
    private void setBackupSummary() {
    	long date = prefs.getLong(LAST_BACKUP, DEFAULT_LAST_BACKUP);
        if (date != DEFAULT_LAST_BACKUP) {
        	ps_backup.setSummary(DateFormat.format("'Last backup:' M/d/yy h:mmaa", date));
        } else
        	ps_backup.setSummary("Last backup: Never");
    }
    
    private String getReminderSummary(String key, String value) {
    	StringBuilder builder = new StringBuilder();
    	
    	builder.append("Every ");
    	builder.append(value);
    	
    	if (key.equals(REMINDER_TIME))
    		builder.append(" hours");
    	else
    		builder.append(" minutes");
    		
    	return builder.toString();
    }
    
    private String getHourSummary(String value) {
		String summary;
		
		if (value.equals("0"))
			summary = "Midnight";
		else if (value.equals("12"))
			summary = "Noon";
		else if (value.equals("6") || value.equals("9"))
			summary = value + ":00 am";
		else if (value.equals("15"))
			summary = "3:00 pm";
		else
			summary = "6:00 pm";
							
		return summary;
    }
 
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if (item.getItemId() == android.R.id.home) {
    		finish();
    		return true;
    	} else
    		return super.onOptionsItemSelected(item);
    }

	@Override
	public boolean onPreferenceClick(Preference p) {
		String key = p.getKey();
		
		if (key.equals(AUTO_SORT)) {
        	cbp_auto_sort.setChecked(true);
        	cbp_custom_sort.setChecked(false);
        	prefs_editor.putInt(SORT_TYPE, TaskListAdapter.AUTO_SORT);
        	prefs_editor.commit();
        	
			// Update homescreen widget (after change has been saved to DB)
			TaskButlerWidgetProvider.updateWidget(this);
			return true;
		}
		
		if (key.equals(CUSTOM_SORT)) {
        	cbp_auto_sort.setChecked(false);
        	cbp_custom_sort.setChecked(true);
        	prefs_editor.putInt(SORT_TYPE, TaskListAdapter.CUSTOM_SORT);
        	prefs_editor.commit();
        	startActivity(new Intent(this, CustomSortActivity.class));
			return true;
		}
		
		if (key.equals(BACKUP)) {
			BackupManager backup_manager = new BackupManager();
			String result = backup_manager.backup();
			
			if (result.equals(BackupManager.BACKUP_OK)) {
				prefs_editor.putLong(LAST_BACKUP, System.currentTimeMillis());
				prefs_editor.commit();
				setBackupSummary();
			}
			
			ToastMaker.toast(this, BackupManager.interpretStringCode(result));
			return true;
		}
		
		if (key.equals(RESTORE)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.dialog_restore_backup)
			.setCancelable(true)
			.setTitle(R.string.pref_restore)
			.setPositiveButton(R.string.menu_restore, new DialogInterface.OnClickListener() {
				
				public void onClick(DialogInterface dialog, int id) {
					BackupManager backup_manager = new BackupManager();
					
					if (backup_manager.doesBackupExist()) {
						// Alarm logic: Restore database
						// * Iterate through list of tasks
						// * 	Cancel alarm
						// *    Cancel existing notifications
						ArrayList<Task> tasks = data_source.getTasks(true, null);
						TaskAlarm alarm = new TaskAlarm();
						for (Task task : tasks) {
							alarm.cancelAlarm(context, task.getID());
							alarm.cancelNotification(context, task.getID());
						}
					}
					
					String result = backup_manager.restore();
					ToastMaker.toast(context, BackupManager.interpretStringCode(result));
					
					if (result.equals(BackupManager.RESTORE_OK)) {
						// Update homescreen widget (after change has been saved to DB)
						TaskButlerWidgetProvider.updateWidget(activity);
					}
					
					dialog.dismiss();
				}
				
			})
			.setNegativeButton(R.string.menu_cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});
			builder.create().show();
			return true;
		}
		
		return false;
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		String key = preference.getKey();
		
		if (key.equals(REMINDER_TIME) || key.equals(ALARM_TIME) || key.equals(VIBRATE_ON_ALARM)) {
			if (key.equals(REMINDER_TIME))
				lp_reminder_time.setSummary(getReminderSummary(REMINDER_TIME, (String) newValue));
			else if (key.equals(ALARM_TIME))
				lp_alarm_time.setSummary(getReminderSummary(ALARM_TIME, (String) newValue));
				
			// Start service which will update all of the task alarms with the new reminder intervals
			WakefulIntentService.acquireStaticLock(this);
			this.startService(new Intent(this, TaskButlerService.class));
			
			return true;   
		}
		
		if (key.equals(DEFAULT_HOUR)) {
			lp_default_hour.setSummary(getHourSummary((String) newValue));
			return true;
		}
			
		return false;
	}
}
