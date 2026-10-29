import { AfterViewInit, Component, ElementRef, Input, OnDestroy, inject, signal } from '@angular/core';
export type AvatarGesture='saludar'|'reposo'|'senalar';
let nextId=0;
@Component({selector:'app-legal-avatar',standalone:true,templateUrl:'./legal-avatar.component.html',styleUrl:'./legal-avatar.component.css'})
export class LegalAvatarComponent implements AfterViewInit,OnDestroy {
 @Input()compact=false;
 readonly uid='illari-rig-'+nextId++;
 readonly mode=signal<AvatarGesture>('saludar');
 readonly reduced=signal(false);readonly visible=signal(true);readonly paused=signal(false);
 readonly animated=()=>!this.reduced();
 private readonly host=inject(ElementRef<HTMLElement>);
 private media?:MediaQueryList;private greeting?:ReturnType<typeof setTimeout>;private observer?:IntersectionObserver;
 private readonly onMotion=()=>{this.reduced.set(!!this.media?.matches);if(this.reduced()){this.cancelGreeting();this.mode.set('reposo');}};
 private readonly onVisibility=()=>this.paused.set(document.hidden);
 ngAfterViewInit():void {
  this.media=window.matchMedia('(prefers-reduced-motion: reduce)');this.onMotion();this.media.addEventListener('change',this.onMotion);
  document.addEventListener('visibilitychange',this.onVisibility);this.onVisibility();
  if(typeof IntersectionObserver!=='undefined'){const observer=new IntersectionObserver(entries=>this.visible.set(entries.some(e=>e.isIntersecting)));if(typeof observer.observe==='function'&&typeof observer.disconnect==='function'){this.observer=observer;observer.observe(this.host.nativeElement);}}
  if(!this.reduced())this.gesture('saludar');
 }
 gesture(name:AvatarGesture):void {
  this.cancelGreeting();
  if(this.reduced()){this.mode.set('reposo');return;}
  this.mode.set(name);
  if(name==='saludar')this.greeting=setTimeout(()=>{this.mode.set('reposo');this.greeting=undefined;},3000);
 }
 private cancelGreeting():void{if(this.greeting!==undefined){clearTimeout(this.greeting);this.greeting=undefined;}}
 ngOnDestroy():void{this.cancelGreeting();this.media?.removeEventListener('change',this.onMotion);document.removeEventListener('visibilitychange',this.onVisibility);this.observer?.disconnect();}
}
