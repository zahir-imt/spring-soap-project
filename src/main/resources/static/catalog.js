(function(root){
 'use strict';
 function status(p){return p.quantity===0?'out':p.quantity<=p.reorderLevel?'low':'healthy';}
 function select(products,{query='',category='',stock='',sort='name'}={}){
  const q=query.trim().toLocaleLowerCase();
  const result=products.filter(p=>(!q||[p.name,p.sku,p.category].join(' ').toLocaleLowerCase().includes(q))&&(!category||p.category===category)&&(!stock||status(p)===stock));
  const compare={name:(a,b)=>a.name.localeCompare(b.name),quantity:(a,b)=>a.quantity-b.quantity,price:(a,b)=>a.price-b.price,value:(a,b)=>b.quantity*b.price-a.quantity*a.price}[sort]||((a,b)=>a.name.localeCompare(b.name));
  return result.sort((a,b)=>compare(a,b)||a.sku.localeCompare(b.sku));
 }
 function summary(products){return {count:products.length,units:products.reduce((sum,p)=>sum+p.quantity,0),value:products.reduce((sum,p)=>sum+p.quantity*p.price,0)};}
 function cell(value){
  let text=String(value??'');
  if(/^[\s\u0000-\u001f]*[=+@-]|^[\t\r\n]/.test(text)) text="'"+text;
  return '"'+text.replace(/"/g,'""')+'"';
 }
 function csv(products){
  const rows=[['SKU','Product','Category','Unit price (CAD)','Quantity','Reorder level','Status','Stock value (CAD)'],...products.map(p=>[p.sku,p.name,p.category,Number(p.price).toFixed(2),p.quantity,p.reorderLevel,{out:'Out of stock',low:'Low stock',healthy:'In stock'}[status(p)],(p.quantity*p.price).toFixed(2)])];
  return '\uFEFF'+rows.map(row=>row.map(cell).join(',')).join('\r\n')+'\r\n';
 }
 const tools={select,summary,csv,status};
 if(typeof module==='object'&&module.exports) module.exports=tools; else root.catalogTools=tools;
})(typeof window==='undefined'?this:window);
