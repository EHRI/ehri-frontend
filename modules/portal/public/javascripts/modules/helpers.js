Object.size = function(obj) {
    var size = 0, key;
    for (key in obj) {
        if (obj.hasOwnProperty(key)) size++;
    }
    return size;
};

function isEmptyObject(obj) {	//http://stackoverflow.com/questions/679915/how-do-i-test-for-an-empty-javascript-object-from-json
    for(var prop in obj) {
        if(obj.hasOwnProperty(prop))
            return false;
    }
    return true;
}