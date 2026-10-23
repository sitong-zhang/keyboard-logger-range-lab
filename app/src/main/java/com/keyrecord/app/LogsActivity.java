package com.keyrecord.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogsActivity extends AppCompatActivity {

    private LogManager logManager;
    private List<LogManager.LogEntry> allEntries = new ArrayList<>();
    private List<LogManager.LogEntry> shownEntries = new ArrayList<>();
    private LogAdapter adapter;
    private TextView tvEmpty;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);
        logManager = LogManager.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        RecyclerView recycler = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogAdapter();
        recycler.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logs_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String q) {
                query = q == null ? "" : q.toLowerCase(Locale.getDefault());
                applyFilter();
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menu_export) {
            java.io.File f = logManager.export();
            Toast.makeText(this,
                    getString(R.string.exported_toast, f.getAbsolutePath()), Toast.LENGTH_LONG).show();
            return true;
        } else if (id == R.id.menu_clear) {
            confirmClear();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEntries();
    }

    private void loadEntries() {
        allEntries = logManager.readEntries();
        Collections.reverse(allEntries); // newest first
        applyFilter();
    }

    private void applyFilter() {
        shownEntries.clear();
        for (LogManager.LogEntry e : allEntries) {
            if (e.event) {
                continue;
            }
            if (query.isEmpty()) {
                shownEntries.add(e);
                continue;
            }
            String label = AppLabels.getLabel(this, e.pkg).toLowerCase(Locale.getDefault());
            if (e.text.toLowerCase(Locale.getDefault()).contains(query)
                    || label.contains(query)) {
                shownEntries.add(e);
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(shownEntries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_confirm_title)
                .setMessage(R.string.clear_confirm_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear, (d, w) -> {
                    logManager.clear();
                    loadEntries();
                    Toast.makeText(this, R.string.clear, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private String formatTime(long millis) {
        if (millis <= 0) {
            return "";
        }
        return new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final TextView tvApp;
            final TextView tvTime;
            final TextView tvSnippet;

            VH(View v) {
                super(v);
                tvApp = v.findViewById(R.id.tvApp);
                tvTime = v.findViewById(R.id.tvTime);
                tvSnippet = v.findViewById(R.id.tvSnippet);
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            LogManager.LogEntry e = shownEntries.get(position);
            h.tvApp.setText(AppLabels.getLabel(LogsActivity.this, e.pkg));
            h.tvTime.setText(formatTime(e.time));
            h.tvSnippet.setText(e.text);
        }

        @Override
        public int getItemCount() {
            return shownEntries.size();
        }
    }
}
