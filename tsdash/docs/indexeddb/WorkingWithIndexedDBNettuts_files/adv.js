var _bsaPRO_loaded=false;function _bsaPRO(){if(_bsaPRO_loaded)return;_bsaPRO_loaded=true;if(document.register)document.register('x-advertiser-pro');var f='//srv.buysellads.com';for(var g=[],zonesegments={},cl=function(a){for(var n=!!document.getElementsByClassName,ret=[],els=n?document.getElementsByClassName(a):document.getElementsByTagName('*'),p=n?false:new RegExp('(^|\\s)'+a+'(\\s|$)'),i=0;i<els.length;i++)if(!p||p.test(els[i].className))ret.push(els[i]);return ret},bs=cl('bsaPROrocks'),segments=[],seg,zoneid,id,p=/bsap_([a-f0-9]+)/i,i=0;i<bs.length&&(zoneid=bs[i].getAttribute('data-serve'))&&(bs[i].className='bsap');i++){if(window['bsa_'+zoneid])continue;var h=bs[i].getAttribute('data-ignore');window['bsa_'+zoneid]=(function(d,e){return function(a){var b=document.createElement('x-advertiser-pro');b.innerHTML=a;if(!h){var c=b.getElementsByTagName('a');for(var i=0;i<c.length;i++)c[i].setAttribute('href',c[i].getAttribute('href').replace('/ads/click/x/','/ads/click/track/yes/x/'))}d.parentNode.insertBefore(b,d)}})(bs[i],zoneid);var c=document.createElement('script');c.type='text\/javascript';c.id='_bsaPRO_js';var j=h?'ignore=yes':'track=1';c.src=f+'/ads/'+zoneid+'.js?'+j;c.setAttribute('async','async');document.getElementsByTagName('head')[0].appendChild(c)}}_bsaPRO();