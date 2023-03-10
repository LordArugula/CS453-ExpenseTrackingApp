package com.example.expense_tracking_app;

import android.content.Intent;
import android.icu.text.NumberFormat;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ExpenseAdapter expenseAdapter;

    private TextView totalText;
    private TextView averageText;

    private ExpenseCategories expenseCategories;
    private CategoryFilter categoryFilter;
    private DateFilter dateFilter;

    private final ActivityResultLauncher<Intent> expenseActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onExpenseActivityResult);

    private final ActivityResultLauncher<Intent> filterActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onFilterActivityResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        List<Expense> expenses = new ArrayList<>();

        String[] expenseCategoriesArray = getResources().getStringArray(R.array.expense_categories);
        expenseCategories = new ExpenseCategories(expenseCategoriesArray, getString(R.string.expense_category_default));

        View expenseFilters = findViewById(R.id.expense_filters);

        expenseFilters.setOnClickListener(view -> launchFilterActivity());

        RecyclerView recyclerView = findViewById(R.id.expenses_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        categoryFilter = new CategoryFilter();
        dateFilter = new DateFilter();
        expenseAdapter = new ExpenseAdapter(expenses, categoryFilter, dateFilter, this::onItemClick);
        recyclerView.setAdapter(expenseAdapter);

        totalText = findViewById(R.id.expense_total);
        averageText = findViewById(R.id.expense_average);
        updateSummary();
    }

    public void onAddExpenseCallback(View view) {
        Expense expense = new Expense("", LocalDate.now(), 0, expenseCategories.getDefault());
        launchExpenseActivity(expense, ExpenseActivity.EXPENSE_NEW);
    }

    private void onItemClick(Expense expense, int position) {
        launchExpenseActivity(expense, position);
    }

    private void launchExpenseActivity(Expense expense, int id) {
        Intent intent = new Intent(this, ExpenseActivity.class);

        intent.putExtra(getString(R.string.EXTRA_EXPENSE_NAME), expense.getName());
        intent.putExtra(getString(R.string.EXTRA_EXPENSE_DATE), expense.getDate().toEpochDay());
        intent.putExtra(getString(R.string.EXTRA_EXPENSE_COST), expense.getCost());
        intent.putExtra(getString(R.string.EXTRA_EXPENSE_CATEGORY), expense.getCategory());
        intent.putExtra(getString(R.string.EXTRA_EXPENSE_REASON), expense.getReason());
        intent.putExtra(getString(R.string.EXTRA_EXPENSE_NOTES), expense.getNotes());
        intent.putExtra(getString(R.string.EXTRA_EXPENSE_ID), id);

        intent.putStringArrayListExtra(getString(R.string.EXTRA_EXPENSE_CUSTOM_CATEGORIES), expenseCategories.getCustomCategories());

        expenseActivityResultLauncher.launch(intent);
    }

    private void onExpenseActivityResult(ActivityResult result) {
        switch (result.getResultCode()) {
            case RESULT_OK:
                assert result.getData() != null;
                insertOrUpdateExpense(result.getData());
                break;
            case RESULT_CANCELED:
                assert result.getData() != null;
                removeExpense(result.getData());
                break;
        }
    }

    private void insertOrUpdateExpense(Intent data) {
        int id = data.getIntExtra(getString(R.string.EXTRA_EXPENSE_ID), ExpenseActivity.EXPENSE_ERROR_ID);
        assert id != ExpenseActivity.EXPENSE_ERROR_ID;

        String name = data.getStringExtra(getString(R.string.EXTRA_EXPENSE_NAME));
        long dateLong = data.getLongExtra(getString(R.string.EXTRA_EXPENSE_DATE), 0);
        double cost = data.getDoubleExtra(getString(R.string.EXTRA_EXPENSE_COST), 0);
        String category = data.getStringExtra(getString(R.string.EXTRA_EXPENSE_CATEGORY));
        String reason = data.getStringExtra(getString(R.string.EXTRA_EXPENSE_REASON));
        String notes = data.getStringExtra(getString(R.string.EXTRA_EXPENSE_NOTES));

        ArrayList<String> customCategories = data.getStringArrayListExtra(getString(R.string.EXTRA_EXPENSE_CUSTOM_CATEGORIES));
        expenseCategories.addCategories(customCategories);

        Expense expense = new Expense(name, LocalDate.ofEpochDay(dateLong), cost, category);
        expense.setReason(reason);
        expense.setNotes(notes);

        if (id == ExpenseActivity.EXPENSE_NEW) {
            expenseAdapter.addItem(expense);
        } else {
            expenseAdapter.updateItem(id, expense);
        }
        updateSummary();
    }

    private void removeExpense(Intent data) {
        boolean delete = data.getBooleanExtra(getString(R.string.EXTRA_EXPENSE_DELETE), false);

        if (delete) {
            int id = data.getIntExtra(getString(R.string.EXTRA_EXPENSE_ID), ExpenseActivity.EXPENSE_ERROR_ID);
            assert id != ExpenseActivity.EXPENSE_ERROR_ID;

            expenseAdapter.removeItem(id);
            updateSummary();
        }
    }

    private void updateSummary() {
        double total = expenseAdapter.getViewItemsCost();
        NumberFormat format = NumberFormat.getCurrencyInstance();
        totalText.setText(format.format(total));

        double average;
        if (expenseAdapter.getItemCount() == 0) {
            average = 0;
        } else {
            average = total / expenseAdapter.getItemCount();
        }
        averageText.setText(format.format(average));
    }

    private void launchFilterActivity() {
        Intent intent = new Intent(this, FilterActivity.class);

        intent.putExtra(getString(R.string.EXTRA_FILTER_BY_DATE), dateFilter.isEnabled());
        if (dateFilter.isEnabled()) {
            intent.putExtra(getString(R.string.EXTRA_FILTER_DATE_START), dateFilter.getStartDate().toEpochDay());
            intent.putExtra(getString(R.string.EXTRA_FILTER_DATE_END), dateFilter.getEndDate().toEpochDay());
        }

        intent.putExtra(getString(R.string.EXTRA_FILTER_BY_CATEGORY), categoryFilter.isEnabled());
        if (categoryFilter.isEnabled()) {
            intent.putExtra(getString(R.string.EXTRA_FILTER_CATEGORY), categoryFilter.getCategory());
        }

        intent.putStringArrayListExtra(getString(R.string.EXTRA_EXPENSE_CUSTOM_CATEGORIES), expenseCategories.getCustomCategories());

        filterActivityResultLauncher.launch(intent);
    }

    private void onFilterActivityResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            Intent data = result.getData();
            assert data != null;

            boolean filterByDate = data.getBooleanExtra(getString(R.string.EXTRA_FILTER_BY_DATE), false);
            boolean filterByCategory = data.getBooleanExtra(getString(R.string.EXTRA_FILTER_BY_CATEGORY), false);

            if (filterByDate) {
                long dateStart = data.getLongExtra(getString(R.string.EXTRA_FILTER_DATE_START), 0);
                long dateEnd = data.getLongExtra(getString(R.string.EXTRA_FILTER_DATE_END), 0);
                dateFilter.setDateRange(LocalDate.ofEpochDay(dateStart), LocalDate.ofEpochDay(dateEnd));
            } else {
                dateFilter.setEnabled(false);
            }

            if (filterByCategory) {
                String category = data.getStringExtra(getString(R.string.EXTRA_FILTER_CATEGORY));
                if (category.contentEquals(getString(R.string.filter_category_all))) {
                    categoryFilter.setEnabled(false);
                } else {
                    categoryFilter.setCategory(category);
                }
            } else {
                categoryFilter.setEnabled(false);
            }

            expenseAdapter.updateFilters();
            updateSummary();
        }
    }
}