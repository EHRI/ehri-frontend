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
  })

  $("#submit-update").click(function(e) {
    var $elem = $(this);
    $elem.attr("disabled", true);
    submitAndPoll($("#update-form").serialize(), function() {
      $elem.attr("disabled", false);
    });
    e.preventDefault();
    e.stopPropagation();
  });

  function appendProgressMessage(msg) {
    var $elem = $("#update-progress");
    var $inner = $("#update-progress > pre");
    if ($inner.length === 0) {
      $inner = $("<pre></pre>");
      $elem.append($inner);
    }
    $inner.append(msg + "<br/>");
    $elem.scrollTop($inner.height());

  }

  function submitAndPoll(data, doneFunc) {
    var pollTimer = -1, nextReadPos = -1;
    var xhReq = new XMLHttpRequest();
    xhReq.open("POST", POLL_URL, true);
    //Send the proper header information along with the request
    xhReq.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    xhReq.send(data);

    // Don't bother with onreadystatechange - it shouldn't close
    // and we're polling responsetext anyway
    pollTimer = setInterval(pollLatestResponse, 250);

    function pollLatestResponse() {
      var allMessages = xhReq.responseText;
      do {
        var unprocessed = allMessages.substring(nextReadPos);
        var messageXMLEndIndex = unprocessed.indexOf("</message>");
        if (messageXMLEndIndex!=-1) {
          var endOfFirstMessageIndex = messageXMLEndIndex + "</message>".length;
          var anUpdate = unprocessed.substring(0, endOfFirstMessageIndex);
          appendProgressMessage(anUpdate);
          nextReadPos += endOfFirstMessageIndex;
          if (anUpdate.indexOf(DONE_MSG) != -1 || anUpdate.indexOf(ERR_MSG) != -1) {
            doneFunc();
            clearInterval(pollTimer);
          }
        }
      } while (messageXMLEndIndex != -1);
    }
  }
});
