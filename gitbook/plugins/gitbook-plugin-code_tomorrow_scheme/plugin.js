require(["gitbook"], function(gitbook) {
    gitbook.events.bind("page.change", function() {
        // do something
        var pre = document.getElementsByTagName('pre'), len = pre.length;
        for(var i=0; i < len; i++){
            pre[i].className = 'prettyprint';
        }
        prettyPrint();
    });

    gitbook.events.bind("exercise.submit", function() {
        // do something
    });
});