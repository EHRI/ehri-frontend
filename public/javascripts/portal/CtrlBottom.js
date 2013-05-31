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
		console.log($scope.basket);
    });
	//Basket --->
	
	//<-- Drag & Drop to basket
	$rootScope.$on('dropEvent', function(evt, dragged, dropped) {
		//dropped.push(dragged);
        dropped[dragged.id] = dragged;
		console.log(Object.size(dropped));
        $scope.$apply();
		//console.log($scope.basket);
		/*
        var i, oldIndex1, oldIndex2;
        for(i=0; i<$scope.columns.length; i++) {
            var c = $scope.columns[i];
            if(dragged.title === c.title) {
                oldIndex1 = i;
            }
            if(dropped.title === c.title) {
                oldIndex2 = i;
            }
        }
        var temp = $scope.columns[oldIndex1];
        $scope.columns[oldIndex1] = $scope.columns[oldIndex2];
        $scope.columns[oldIndex2] = temp;
		*/
    });
	//-->
	
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