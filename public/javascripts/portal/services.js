portal.factory('myPaginationService', function($rootScope) {
    var paginationService = {};
    
    paginationService.paginationDatas = {'current' : 1, 'max': 5, 'num': false};

    paginationService.prepForBroadcast = function(msg) {
        this.paginationDatas = msg;
        this.broadcastItem();
    };

    paginationService.broadcastItem = function() {
        $rootScope.$broadcast('handlePaginationBroadcast');
    };
	
    paginationService.changePage = function(page) {
        this.page = page;
        this.broadcastBottomItem();
    };

    paginationService.broadcastBottomItem = function() {
        $rootScope.$broadcast('handlePaginationBottomBroadcast');
    };
    return paginationService;
}).factory('myBasketService', function($rootScope) {
    var basketservice = {};
    
    basketservice.toBasket = {};

    basketservice.add = function(item) {
		// console.log(this);
		this.toBasket = item;
        //this.list.push(item);
        this.broadcastBasket();
    };
	
    basketservice.broadcastBasket = function() {
        $rootScope.$broadcast('handleBasketBroadcast');
    };
	
    return basketservice;
}).factory('Item', function($http){
	var Item = {}
	
	Item.query = function(type, item) {
		return $http.get('./api/'+type+'/'+item).success(function(data) {
			Item.data = data;
		});
	}
	
	return Item;
}); 