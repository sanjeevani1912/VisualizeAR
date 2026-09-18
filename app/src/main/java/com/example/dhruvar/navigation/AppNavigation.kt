package com.example.dhruvar.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.ui.screens.HomeScreen
import com.example.dhruvar.ui.screens.PlanningScreen
import com.example.dhruvar.ui.screens.VisualizationScreen
import com.example.dhruvar.viewmodel.PlanningViewModel

/**
 * Top-level application navigation graph.
 *
 * Establishes the decoupled flow:
 * Home -> Planning -> Visualization -> Planning
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    planningViewModel: PlanningViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCreateNewLayout = {
                    planningViewModel.createNewLayout()
                    navController.navigate(Screen.Planning.route)
                },
                onOpenLayout = { layout: Layout ->
                    planningViewModel.loadLayout(layout)
                    navController.navigate(Screen.Planning.route)
                },
                viewModel = planningViewModel
            )
        }

        composable(Screen.Planning.route) {
            PlanningScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToVisualization = {
                    navController.navigate(Screen.Visualization.route)
                },
                viewModel = planningViewModel
            )
        }

        composable(Screen.Visualization.route) {
            VisualizationScreen(
                onNavigateBackToPlanning = {
                    navController.popBackStack()
                },
                viewModel = planningViewModel
            )
        }
    }
}
