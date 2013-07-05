var Ba = portal.controller('BasketCtrl', ['$scope', '$filter', 'myBasketService', '$http', function($scope, $filter, $basket, $http) {
	$scope.basket = { content: []}
	$scope.basket.raw = $basket.get();
	console.log($basket.get());
	
	//TEST DATA : $scope.basket.raw = [ { id:"ehri-pers-000185", type:"historicalAgent"},  { id:"bf9080b0-c591-4a27-b64a-9865daab4fc2", type:"cvocConcept"},  { id:"at-002004", type:"repository"},  { id:"005578-503089-504088-504110", type:"documentaryUnit"},  { id:"ehri-cb-1278", type:"historicalAgent"}];
	
	$scope.basket.loading = true;
	
	$scope.load = function (id) {
		console.log(id);
		var key = parseInt(id) - 1; // Key ids
		
		var currentItem = $scope.basket.raw[key];
		$http.get('./api/'+currentItem.type+'/'+currentItem.id).success(function(data) {
			$scope.basket.content.push(data);
			if(($scope.basket.raw.length - parseInt(id)) > 0)
			{
				var next = 1 + parseInt(id) ;
				$scope.load(next);
			}
			else {
				$scope.basket.loading = false;
			}
		});
		
		
	}
	
	//<-- Load data 
	if($scope.basket.raw.length > 0) { $scope.load(1); }
	else { $scope.basket.loading = false; }
	// Load data-->
}]);