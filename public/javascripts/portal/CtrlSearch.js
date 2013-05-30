portal.controller('SearchCtrl', ['$scope', '$http', '$routeParams', '$location', '$service', '$anchorScroll', 'myPaginationService', 'myBasketService', function($scope, $http, $routeParams, $location, $service, $anchorScroll, paginationService, $basket) {
	//Scope var
	$scope.searchParams = {'page' : 1, 'sort' : 'score.desc'};
	$scope.langFilter = "en";
	
	$scope.pages = {};
	
	$scope.currentPage = 1; //Current page of pagination
	$scope.justLoaded = false;
	$scope.maxSize = 5; // Number of pagination buttons shown
	$scope.numPages = false; // Number of pages (get from query)
	$scope.loadedPage = {1 : true};
	
	
	$scope.lastFilter = false;
	$scope.loadingPage = false;
	
	$scope.fromSearch = function() {
		angular.forEach($location.search(), function(value, key) {
			$scope.searchParams[key] = value;
			if(key == "q")
			{
				$scope.searchTerm = value;
			}
			else if(key == "page")
			{
				$scope.currentPage = parseInt(value);
				$scope.loadedPage[parseInt(value)] = true;
			}
		});
	}
	
	//<----
	//Pagination System with outside pagination render
	$scope.$watch('currentPage + numPages', function(newValue) {
		//{'current' : 1, 'max': 10, 'num': false}
		paginationService.prepForBroadcast({'current' : $scope.currentPage, 'max': 5, 'num': $scope.numPages});
	});
	
    $scope.$on('handlePaginationBottomBroadcast', function() {
		var prev = $scope.currentPage;
        $scope.currentPage = paginationService.page;
		//console.log($scope.loadedPage);
		if($scope.loadedPage[$scope.currentPage])
		{
			$location.hash('page-' + $scope.currentPage);
			$anchorScroll();
		}
		else
		{
		
			$scope.searchParams.page = $scope.currentPage;	
			$scope.doSearch(true, (prev < $scope.currentPage));
			console.log("load new page");
		}
    });
	
	//---->
	
	//<--- Basket function
	$scope.addBasket = function(item) {
		$basket.add(item);
	}
	//Basket Function --->
	
	
	//<---QUERY FUNCTIONS
	//Query functions
	$scope.setQuery = function(type, q) {
		if($scope.searchParams[type] == q && type != 'page' && type != 'q' && type != 'sort')
		{
			$scope.removeFilterByKey(type);
			$location.search('page', 1);
			$scope.searchParams.page = 1;
			$scope.currentPage = 1;
		}
		else
		{
			if(type != 'page')
			{
				$location.search('page', 1);
				$scope.searchParams.page = 1;
				$scope.currentPage = 1;
			}
			else {
				q = parseInt(q);
			}
			$scope.lastFilter = type;
			$scope.searchParams[type] = q;
			$location.search(type, q);
			
			$scope.doSearch();
		}
	}
	
	$scope.getUrl = function(url) {
		url = url + '?';
		
		var urlArr = [];
		angular.forEach($scope.searchParams, function(value, key) {
			urlArr.push(key + "=" + value);
		});
		
		return url + urlArr.join('&');
	}
	
	$scope.doSearch = function(push, scroll) {	
		if(!push) { $scope.searchParams.page = 1; $scope.currentPage = 1; }
		url = $scope.getUrl('/search');
		$http.get(url, {headers: {'Accept': "application/json"}}).success(function(data) {
			if(push)
			{
				//Data themself
				$scope.pages[$scope.currentPage] = {};
				$scope.pages[$scope.currentPage].items = data.page.items;
				$scope.pages[$scope.currentPage].items[0].page = $scope.currentPage;
				
				//Facets
				$scope.facets = data.facets;
				
				//Pagination
				$scope.loadedPage[$scope.currentPage] = true;
				if(scroll)
				{
					$location.hash('page-' + $scope.currentPage);
					$anchorScroll();
				}
			}
			else
			{
				//Datas
				$scope.currentPage = 1;
				$scope.loadedPage = {1 : true};
				$scope.page = data.page;
				$scope.pages = {};
				$scope.pages[$scope.currentPage] = {};
				$scope.pages[$scope.currentPage].items = data.page.items;
				if(data.page.total > 0)
				{
					$scope.pages[$scope.currentPage].items[0].page = $scope.currentPage;
				}
				$scope.facets = data.facets;
				
				//Pagination
				$scope.loadedPage = {};
				$scope.loadedPage[$scope.currentPage] = true;
				$scope.numPages = data.numPages;
			}
			// console.log($scope.items);
			//Get the loading possible again
			$scope.loadingPage = false;
		}).error(function() { 
			$scope.removeFilterByKey($scope.lastFilter);
			alert('Server error, reloading datas');
		});
	}
	
	$scope.loadMore = function () {
		if($scope.loadingPage == false && $scope.currentPage != $scope.numPages && (!$scope.loadedPage[($scope.currentPage + 1)]))
		{
			$scope.loadingPage = true;
			$scope.currentPage = $scope.currentPage + 1;
			$scope.searchParams.page = $scope.currentPage;
			$scope.doSearch(true);
			//console.log("ScrolltoCall for page " + $scope.currentPage);
		}
	}
	
	$scope.removeFilterByKey = function(key){
		delete $scope.searchParams[key];
		$location.search(key, null);
		$scope.doSearch();
		return true;
	}
	
	$scope.removeFilter = function (value) {
		angular.forEach($scope.searchParams, function(v,k) {
			if(v == value)
			{
				delete $scope.searchParams[k];
			}
		});
		angular.forEach($location.search(), function(v, k) {
			if(v == value)
			{
				$location.search(k, null);
			}
		});
		$scope.doSearch();
		return true;
	}
	//QUERY FUNCTIONS ---->
	
/**********
**
**
**
**
*/
	$scope.getLink = function(type,id) {
        location.href = $service.redirectUrl(type, id);
	}
	//Description functions	
	$scope.getTitleAction = function(item) {
		//2013-05-15 15:42:23 Mike Bryant: Imported from command-line
		if(item.relationships.lifecycleEvent && item.relationships.lifecycleEvent[0])
		{
			event = item.relationships.lifecycleEvent[0];
			message = event.data.logMessage;
			return message;
		}
	}
	
	$scope.getSimpleTimeDesc = function(item) {
		//console.log(item);
		//Updated 5 days ago
		if(item.relationships.lifecycleEvent && item.relationships.lifecycleEvent[0])
		{
			event = item.relationships.lifecycleEvent[0];
			//d = new Date(event.data.timestamp);
			return event.data.timestamp;
		}
	}
	
	$scope.fromSearch();
	$scope.doSearch();
}]);