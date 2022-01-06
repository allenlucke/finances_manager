import { HttpErrorResponse } from "@angular/common/http";

export class ApiError extends HttpErrorResponse{

    timestamp!: string;
    status!: number;
    error!: any;
    message!: string;
    path!: string;
}
