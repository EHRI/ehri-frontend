/**
 * Helper JS for doing a long-running index job and getting progress.
 *
 * NB: Three constants have to be initialised in the templates at present. These
 * are POLL_URL, DONE_MSG and ERR_MSG, and are used to parse the streaming response from
 * the server.
 */

jQuery(function($) {

  $("#select-all").change(function(event) {
    $("input[name='type[]']").prop("checked", $(this).prop("checked"));
  });

  $("#submit-update").click(function(e) {
    e.preventDefault();
    e.stopPropagation();
    var $elem = $(this);
    $elem.attr("disabled", true);
    submitAndRead($("#update-form").serialize(), function() {
      $elem.attr("disabled", false);
    });
  });

  function appendProgressMessage(msg) {
    var $elem = $("#update-progress");
    var $inner = $elem.find("> pre");
    if ($inner.length === 0) {
      $inner = $("<pre></pre>");
      $elem.append($inner);
    }
    $inner.append(msg + "<br/>");
    $elem.scrollTop($inner.height());
  }

  function submitAndRead(data, doneFunc) {
    var pollTimer = -1, nextReadPos = 0;
    var xhReq = new XMLHttpRequest();
    xhReq.open("POST", POLL_URL, true);
    xhReq.timeout = 0;
    xhReq.onprogress = function() {
      var allMessages = xhReq.responseText;
      var tag = "</message>";
      var bufLength = tag.length;
      do {
        var unprocessed = allMessages.substring(nextReadPos);
        var messageXMLEndIndex = unprocessed.indexOf(tag);
        if (messageXMLEndIndex!=-1) {
          var endOfFirstMessageIndex = messageXMLEndIndex + bufLength;
          var newData = unprocessed.substring(0, endOfFirstMessageIndex);
          appendProgressMessage(newData);
          nextReadPos += endOfFirstMessageIndex;
          if (newData.indexOf(DONE_MSG) != -1 || newData.indexOf(ERR_MSG) != -1) {
            doneFunc();
          }
        }
      } while (messageXMLEndIndex != -1);
    };


    //Send the proper header information along with the request
    xhReq.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    xhReq.send(data);
  }
});
