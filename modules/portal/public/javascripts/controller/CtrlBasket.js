var Ba = portal.controller('BasketCtrl', ['$scope', 'portal', '$filter', 'myBasketService', '$http', function($scope, $portal, $filter, $basket, $http) {
	$scope.basket = {
		content: [],	//Content of basket
		raw: $basket.get(),	//Gets data from $basket service
		load :	function (id) {	//Load data by item
		
			var key = parseInt(id) - 1; // Key ids for array, so starting at 0 ( 1 -1 )
			var currentItem = $scope.basket.raw[key];	//Current item in raw data
			//console.log(currentItem.type);
			$http.get($portal.item[currentItem.type].get(currentItem.id), {headers: {'Accept': "application/json"}}).success(function(data) {
				$scope.basket.content.push(data);
				if(($scope.basket.raw.length - parseInt(id)) > 0)	{ //We load next item if there is one
					var next = 1 + parseInt(id) ;
					$scope.basket.load(next);
				}
				else {
					$scope.ui.general.loading = false;
				}
			});
		}
	}
	$scope.ui = {
		general : {
			loading : true
		}
	}
	
	//<-- Load data 
	if($scope.basket.raw.length > 0) { $scope.basket.load(1); }	// We load if had data in raw
	else { $scope.ui.general.loading = false; }	//Stop loading icon if not
	// Load data-->
}]);