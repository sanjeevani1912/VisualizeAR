package com.example.dhruvar.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.ui.screens.ARVisualizationScreen
import com.example.dhruvar.ui.screens.FieldGuideScreen
import com.example.dhruvar.ui.screens.HomeScreen
import com.example.dhruvar.ui.screens.PlanningScreen
import com.example.dhruvar.ui.screens.SignInScreen
import com.example.dhruvar.viewmodel.AccountViewModel
import com.example.dhruvar.viewmodel.PlanningViewModel

/**
 * Top-level application navigation graph.
 *
 * Flow: Home -> Planning -> AR Visualization; Home -> Field Guide
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    planningViewModel: PlanningViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel()
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
                onOpenFieldGuide = {
                    navController.navigate(Screen.FieldGuide.route)
                },
                onOpenSignIn = {
                    navController.navigate(Screen.SignIn.route)
                },
                viewModel = planningViewModel,
                accountViewModel = accountViewModel
            )
        }

        composable(Screen.SignIn.route) {
            SignInScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignedIn = { navController.popBackStack() },
                viewModel = accountViewModel
            )
        }

        composable(Screen.Planning.route) {
            PlanningScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToVisualization = {
                    navController.navigate(Screen.ARVisualization.route)
                },
                viewModel = planningViewModel
            )
        }

        composable(Screen.ARVisualization.route) {
            ARVisualizationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = planningViewModel
            )
        }

        composable(Screen.FieldGuide.route) {
            FieldGuideScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
