package com.yeivikas.olyzecs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yeivikas.olyzecs.data.ProjectStorage

class ProjectsViewModelFactory(
    private val projectStorage: ProjectStorage
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProjectsViewModel(projectStorage) as T
    }
}
