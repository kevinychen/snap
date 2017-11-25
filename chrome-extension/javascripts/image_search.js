// https://stackoverflow.com/questions/4998908/convert-data-uri-to-file-then-append-to-formdata
function dataURItoBlob(dataURI) {
    // convert base64/URLEncoded data component to raw binary data held in a string
    var byteString;
    if (dataURI.split(',')[0].indexOf('base64') >= 0)
        byteString = atob(dataURI.split(',')[1]);
    else
        byteString = unescape(dataURI.split(',')[1]);

    // separate out the mime component
    var mimeString = dataURI.split(',')[0].split(':')[1].split(';')[0];

    // write the bytes of the string to a typed array
    var ia = new Uint8Array(byteString.length);
    for (var i = 0; i < byteString.length; i++) {
        ia[i] = byteString.charCodeAt(i);
    }

    return new Blob([ia], {type:mimeString});
}

function redirectToImageSearch() {
  // code from https://stackoverflow.com/a/7404033/934239
  var formData = new FormData();
  formData.append('image_url', '');
  formData.append('btnG', 'Search');
  formData.append('encoded_image', dataURItoBlob(document.getElementById('show-canvas').toDataURL()));
  formData.append('image_content', '');
  formData.append('filename', '');
  formData.append('h1', 'en');
  formData.append('bih', '507');
  formData.append('biw', '1920');
  var xhr = new XMLHttpRequest();
  xhr.open("POST", "https://images.google.com/searchbyimage/upload");
  xhr.onreadystatechange = function() {
      if (xhr.readyState == XMLHttpRequest.DONE) {
          console.log(xhr);
          window.location = xhr.responseURL;
      }
  }
  xhr.send(formData);
}

