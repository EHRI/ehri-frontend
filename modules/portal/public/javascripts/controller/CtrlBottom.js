portal.controller('BottomBar', ['$scope', '$http', '$rootScope', 'myPaginationService', 'myBasketService', function($scope, $http, $rootScope, paginationService, $basket) {
    $scope.$on('handlePaginationBroadcast', function() {
        $scope.pagination = paginationService.paginationDatas;
		//{'current' : 1, 'max': 10, 'num': false}
    });
	$scope.$watch('pagination.current', function(newVal) {
		//console.log(newVal);
		paginationService.changePage(newVal);
	});
	
	$scope.loadSavedFiles = function () {
		$http.get(ANGULAR_ROOT + "/files.json")
			.success(function(data) {
			$scope.items = data.items;
		}).error(function() { 
			alert('Server error, ctrl+f5 page please');
		});
	}
	
	//<--- Basket
	$scope.basket = {};
	$scope.basketSize = function() {
		return Object.size($scope.basket) || 0;
	}
    $scope.$on('handleBasketBroadcast', function() {
        $scope.basket[$basket.toBasket.id] = $basket.toBasket;
		//console.log($scope.basket);
    });
	//Basket --->
	
	//<-- Drag & Drop to basket
	$rootScope.$on('dropEvent', function(evt, dragged, dropped) {
        dropped[dragged.id] = dragged;
		console.log(Object.size(dropped));
        $scope.$apply();
    });
	//-->
	
	//<--- DropUp
	$scope.savedOpen = "";
	$scope.notesOpen = "";
	$scope.basketOpen= "";
	$scope.peopleOpen= "";
	$scope.peopleContainer = function() {
		if($scope.peopleOpen === "open")
		{
			$scope.peopleOpen = "";
		}
		else
		{
			$scope.peopleOpen = "open";
		}
	}
	$scope.basketContainer = function() {
		if($scope.basketOpen === "open")
		{
			$scope.basketOpen = "";
		}
		else
		{
			$scope.basketOpen = "open";
		}
	}
	$scope.savedContainer = function() {
		if($scope.savedOpen === "open")
		{
			$scope.savedOpen = "";
		}
		else
		{
			$scope.savedOpen = "open";
			$scope.loadSavedFiles();
		}
	}
	$scope.notesContainer = function() {
		if($scope.notesOpen === "open")
		{
			$scope.notesOpen = "";
		}
		else
		{
			$scope.notesOpen = "open";
		}
	}
	//Dropup --->
	
	//<-- People Form
	$scope.loadingPeople = false;
	$scope.peoples
	$scope.searchPeople = function(people) {
		$scope.loadingPeople = true;
		url = '/search?type=userProfile&q=' + people;
		$http.get(url, {headers: {'Accept': "application/json"}}).success(function(data) {
			$scope.peoples = data.page.items;
			$scope.loadingPeople = false;
		});
	}
	// People Form -->
	
	//<-- Annotation
	$scope.annotation = {'userText' : ""}
	$rootScope.$on('getAnnotation', function(evt, item, text) {
	console.log(text);
		$scope.annotation.selText = text;
		$scope.annotation.item = item;
		$scope.notesOpen = "open";
        $scope.$apply();
    });
	
	// Annotation-->
}]);