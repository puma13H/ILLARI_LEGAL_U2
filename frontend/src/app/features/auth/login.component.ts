import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { destinationForRoles } from '../../core/auth/auth-destination';
@Component({
 selector:'app-login',standalone:true,imports:[RouterLink],changeDetection:ChangeDetectionStrategy.Eager,
 templateUrl:'./login.component.html',styleUrl:'./login.component.css'
})
export class LoginComponent implements OnInit {
 loading=false;error='';infoMsg='';
 constructor(private auth:AuthService,private router:Router,private route:ActivatedRoute){}
 ngOnInit():void {
  if(this.auth.autenticado()){
   const destination=destinationForRoles(this.auth.roles());
   if(destination){void this.router.navigateByUrl(destination,{replaceUrl:true});return;}
   this.error='Tu cuenta no tiene un rol autorizado en ILLARI LEGAL.';
  }
  if(this.route.snapshot.queryParamMap.has('session_expired'))this.infoMsg='Tu sesión expiró. Inicia sesión nuevamente.';
 }
 async submit():Promise<void>{
  if(this.loading)return;
  this.loading=true;this.error='';
  try{await this.auth.iniciarLogin('/dashboard');}
  catch{this.error='Servicio de autenticación no disponible. No se pudo conectar con Keycloak. Inténtalo nuevamente más tarde; puedes seguir consultando la página pública y la tienda.';this.loading=false;}
 }
}
