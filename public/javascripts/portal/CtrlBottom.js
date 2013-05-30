portal.controller('BottomBar', ['$scope', '$http', 'myPaginationService', 'myBasketService', function($scope, $http, paginationService, $basket) {
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
	$scope.basket = [];
    $scope.$on('handleBasketBroadcast', function() {
        $scope.basket = $basket.list;
		console.log($scope.basket);
    });
	//Basket --->
	
	//<--- DropUp
	$scope.savedOpen = "";
	$scope.notesOpen = "";
	$scope.basketOpen= "";
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
}]);